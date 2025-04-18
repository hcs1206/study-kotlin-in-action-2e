package io.study.kotlin.lotto

enum class Winnings (
    val countOfMatch: Int,
    val price: Long
) {
    THREE(3, 5_000),
    FOUR(4, 50_000),
    FIVE(5, 1_500_000),
    SIX(6, 2_000_000_000,);
}