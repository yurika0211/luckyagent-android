package com.luckyagent.android.ui

/** A successful older page must reduce the oldest server ordinal, including the final page. */
internal fun searchHistoryPageProgress(previousStart: Int, currentStart: Int): Result<Boolean> =
    if (previousStart <= 0 || currentStart < 0 || currentStart >= previousStart) {
        Result.failure(IllegalStateException("历史分页没有前进，请重试搜索"))
    } else {
        Result.success(currentStart > 0)
    }
