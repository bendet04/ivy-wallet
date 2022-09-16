package com.ivy.core.domain.action.calculate.transaction

import com.ivy.common.time
import com.ivy.common.timeNowLocal
import com.ivy.core.domain.action.FlowAction
import com.ivy.core.domain.action.calculate.CalculateFlow
import com.ivy.core.domain.pure.calculate.transaction.batchTrns
import com.ivy.core.domain.pure.calculate.transaction.groupActualTrnsByDate
import com.ivy.core.domain.pure.transaction.overdue
import com.ivy.core.domain.pure.transaction.upcoming
import com.ivy.core.domain.pure.util.actualDate
import com.ivy.core.domain.pure.util.actualTrns
import com.ivy.core.domain.pure.util.extractTrns
import com.ivy.core.persistence.dao.trn.TrnLinkRecordDao
import com.ivy.data.transaction.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(FlowPreview::class)
class GroupTrnsFlow @Inject constructor(
    private val calculateFlow: CalculateFlow,
    private val trnLinkRecordDao: TrnLinkRecordDao,
) : FlowAction<List<Transaction>, TransactionsList>() {

    override fun List<Transaction>.createFlow(): Flow<TransactionsList> =
        trnLinkRecordDao.findAll().map { links ->
            batchTrns(trns = this, links = links)
        }.flatMapMerge { batchedTrnItems ->
            combine(
                dueSectionFlow(
                    trnListItems = batchedTrnItems,
                    dueFilter = ::upcoming,
                    createSection = ::UpcomingSection
                ),
                dueSectionFlow(
                    trnListItems = batchedTrnItems,
                    dueFilter = ::overdue,
                    createSection = ::OverdueSection
                ),
                historyFlow(trnListItems = batchedTrnItems),
            ) { upcomingSection, overdueSection, history ->
                TransactionsList(
                    upcoming = upcomingSection,
                    overdue = overdueSection,
                    history = history,
                )
            }
        }.flowOn(Dispatchers.Default)


    // region Upcoming & Overdue sections
    private fun <T> dueSectionFlow(
        trnListItems: List<TrnListItem>,
        dueFilter: (Transaction, now: LocalDateTime) -> Boolean,
        createSection: (income: Value, expense: Value, trns: List<Transaction>) -> T
    ): Flow<T> {
        val now = timeNowLocal()
        val dueTrns = trnListItems.mapNotNull {
            when (it) {
                is TrnListItem.Trn -> it.trn
                else -> null
            }
        }.filter { dueFilter(it, now) }

        return calculateFlow(
            CalculateFlow.Input(
                trns = dueTrns,
                includeTransfers = false,
            )
        ).map { dueStats ->
            // the soonest due date should appear first
            val sortedTrns = dueTrns.sortedBy { it.time.time() }

            createSection(
                dueStats.income,
                dueStats.expense,
                sortedTrns
            )
        }.flowOn(Dispatchers.Default)
    }
    // endregion

    // region History
    private fun historyFlow(
        trnListItems: List<TrnListItem>
    ): Flow<List<TrnListItem>> {
        val actualTrns = actualTrns(trnItems = trnListItems)
        val trnsByDay = groupActualTrnsByDate(actualTrns = actualTrns)

        // emit so the waiting for it "combine" doesn't get stuck
        if (trnsByDay.isEmpty()) return flow { emit(emptyList()) }

        // calculate stats for each trn history day
        return combine(
            trnsByDay.map { (day, trnsForTheDay) ->
                trnHistoryDayFlow(
                    day = day,
                    unsortedTrns = trnsForTheDay,
                )
            }
        ) { trnsPerDay ->
            trnsPerDay.flatMap { it }
        }
    }

    private fun trnHistoryDayFlow(
        day: LocalDate,
        unsortedTrns: List<TrnListItem>
    ): Flow<List<TrnListItem>> = calculateFlow(
        CalculateFlow.Input(
            trns = unsortedTrns.flatMap(::extractTrns),
            outputCurrency = null,
            includeTransfers = true,
        )
    ).map { statsForTheDay ->
        listOf(
            TrnListItem.DateDivider(
                date = day,
                cashflow = statsForTheDay.balance,
            )
        ).plus(
            // Newest transactions should appear at the top
            unsortedTrns.sortedByDescending(::actualDate)
        )
    }
    // endregion
}