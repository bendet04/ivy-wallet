package com.ivy.core.domain.pure.calculate.transaction

import androidx.annotation.IntRange
import com.ivy.common.timeNowUTC
import com.ivy.core.domain.pure.dummy.dummyTrn
import com.ivy.data.transaction.TrnListItem
import com.ivy.data.transaction.TrnTime
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class GroupTrnsTest : StringSpec({
    // region Helper functions
    fun date(daysFromNow: Int): LocalDate = timeNowUTC()
        .withHour(0)
        .withHour(0)
        .plusDays(daysFromNow.toLong()).toLocalDate()

    fun trn(
        daysFromNow: Int,
        @IntRange(from = 0, to = 23) hour24h: Int
    ) = TrnListItem.Trn(
        trn = dummyTrn(
            time = TrnTime.Actual(
                date(daysFromNow).atTime(hour24h, 0, 0)
            )
        )
    )

    fun transfer(
        daysFromNow: Int,
        @IntRange(from = 0, to = 23) hour24h: Int
    ) = TrnListItem.Transfer(
        from = dummyTrn(),
        to = dummyTrn(),
        fee = null,
        batchId = "",
        time = TrnTime.Actual(
            date(daysFromNow).atTime(hour24h, 0, 0)
        )
    )
    // endregion

    "group transactions by date and sort them" {
        val today = trn(daysFromNow = 0, hour24h = 12)
        val yesterday1 = trn(daysFromNow = -1, hour24h = 22)
        val yesterday2 = transfer(daysFromNow = -1, hour24h = 20)
        val fiveDaysBefore1 = trn(daysFromNow = -5, hour24h = 11)
        val fiveDaysBefore2 = trn(daysFromNow = -5, hour24h = 10)
        val fiveDaysBefore3 = transfer(daysFromNow = -5, hour24h = 0)
        val trns = listOf(
            today,
            yesterday1, yesterday2,
            fiveDaysBefore1, fiveDaysBefore2, fiveDaysBefore3
        ).shuffled()

        val trnsByDay = groupActualTrnsByDate(actualTrns = trns)

        trnsByDay shouldBe mapOf(
            date(0) to listOf(today),
            date(-1) to listOf(yesterday1, yesterday2),
            date(-5) to listOf(
                fiveDaysBefore1, fiveDaysBefore2, fiveDaysBefore3
            )
        )
    }
})