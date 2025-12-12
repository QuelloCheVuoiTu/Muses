package it.unisannio.muses.data.models

data class DailyReward(
    val id: Int,
    val dayName: String,
    val dayNumber: Int,
    val rewardType: String, // "points", "discount", "gadget", "bonus", ecc.
    val rewardValue: Int, // Valore dello sconto (es. 10 per 10%) o id del gadget
    val rewardDescription: String, // Descrizione leggibile della ricompensa
    val iconResource: Int,
    var isAvailable: Boolean,
    var isClaimed: Boolean,
    val isToday: Boolean,
    val basePoints: Int = 1000 // Ogni ricompensa vale sempre 1000 punti riscattabili
)