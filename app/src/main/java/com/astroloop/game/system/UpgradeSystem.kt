package com.astroloop.game.system

import com.astroloop.game.core.GameConfig
import com.astroloop.game.core.GameState
import com.astroloop.game.data.PassiveDefinitions
import com.astroloop.game.data.WeaponDefinitions
import com.astroloop.game.entity.EntityPool
import com.astroloop.game.entity.PowerUp
import com.astroloop.game.entity.PowerUpType
import kotlin.random.Random

data class UpgradeOption(
    val id: String,
    val isWeapon: Boolean,
    val isEvolution: Boolean = false,
    val baseWeaponId: String? = null,
    val requiredPassiveId: String? = null,
    val isFallback: Boolean = false,  // True for health/gold options when fully upgraded
    val fallbackType: FallbackType? = null
)

enum class FallbackType {
    HEALTH_RESTORE,  // Restore 20% health
    GOLD_BONUS       // Bonus gold
}

class UpgradeSystem(
    private val powerUpPool: EntityPool<PowerUp>
) {
    private var pendingOptions: List<UpgradeOption> = emptyList()

    var unlockedWeaponIds: Set<String> = emptySet()
    var unlockedPassiveIds: Set<String> = emptySet()

    fun generateUpgradeOptions(state: GameState, fromAsteroid: Boolean = false): List<UpgradeOption> {
        val options = mutableListOf<UpgradeOption>()
        val selected = mutableSetOf<String>()

        // Check if fully upgraded to level/stack limits
        if (state.isFullyUpgraded()) {
            pendingOptions = emptyList()
            return emptyList()
        }

        // Passives that can only be picked once
        val oneTimePassives = setOf("phoenix_core", "extra_weapon_slot", "glass_cannon", "duplicator_core", "lucky_star")

        // Collect owned upgrades for preferential selection
        val ownedWeapons = mutableListOf<UpgradeOption>()
        val ownedPassives = mutableListOf<UpgradeOption>()
        val newWeapons = mutableListOf<UpgradeOption>()
        val newPassives = mutableListOf<UpgradeOption>()

        val ownedWeaponCount = state.weaponLevels.count { it.value > 0 }
        val gatedWeapons = mutableListOf<UpgradeOption>()

        // Categorize weapons (Capped at WEAPON_MAX_LEVEL)
        for (weaponDef in WeaponDefinitions.getBaseWeapons()
            .filter { it.id in unlockedWeaponIds && !state.hasEvolutionOf(it.id) }) {
            val currentLevel = state.getWeaponLevel(weaponDef.id)
            if (currentLevel in 1..<GameConfig.WEAPON_MAX_LEVEL) {
                val hardGateMet = ownedWeaponCount >= currentLevel
                val softGatePasses = hardGateMet ||
                    Random.nextFloat() < ownedWeaponCount.toFloat() / currentLevel.toFloat()
                if (softGatePasses) {
                    ownedWeapons.add(UpgradeOption(weaponDef.id, isWeapon = true))
                } else {
                    gatedWeapons.add(UpgradeOption(weaponDef.id, isWeapon = true))
                }
            } else if (currentLevel == 0 && state.canAddNewWeapon()) {
                newWeapons.add(UpgradeOption(weaponDef.id, isWeapon = true))
            }
        }

        // Categorize passives (Capped at PASSIVE_MAX_STACKS)
        for (passiveDef in PassiveDefinitions.getAllPassives().filter { it.id in unlockedPassiveIds }) {
            val currentStacks = state.getPassiveStacks(passiveDef.id)

            // Skip maxed passives or one-time passives
            if (currentStacks >= GameConfig.PASSIVE_MAX_STACKS) continue
            if (passiveDef.id in oneTimePassives && currentStacks > 0) continue
            if (passiveDef.id == "extra_weapon_slot" && state.hasExtraWeaponSlot) continue

            // Drone passive exclusion
            if (passiveDef.id == "combat_drone" && state.activePilotId == PassiveDefinitions.ASTRO_PILOT_ID) continue
            if (passiveDef.id == "tb26" && state.activePilotId != PassiveDefinitions.ASTRO_PILOT_ID) continue

            if (currentStacks > 0) {
                ownedPassives.add(UpgradeOption(passiveDef.id, isWeapon = false))
            } else if (state.canAddNewPassive()) {
                newPassives.add(UpgradeOption(passiveDef.id, isWeapon = false))
            }
        }

        val ownedUpgrades = ownedWeapons + ownedPassives
        val newUpgrades = newWeapons + newPassives

        // Select remaining options: 50% chance to offer owned upgrades (if any exist)
        while (options.size < GameConfig.UPGRADE_CHOICES) {
            val candidate: UpgradeOption? = when {
                ownedUpgrades.any { !selected.contains(it.id) } && Random.nextFloat() < 0.5f -> {
                    ownedUpgrades.filter { !selected.contains(it.id) }.randomOrNull()
                }
                newUpgrades.any { !selected.contains(it.id) } -> {
                    newUpgrades.filter { !selected.contains(it.id) }.randomOrNull()
                }
                ownedUpgrades.any { !selected.contains(it.id) } -> {
                    ownedUpgrades.filter { !selected.contains(it.id) }.randomOrNull()
                }
                else -> null
            }

            if (candidate != null) {
                options.add(candidate)
                selected.add(candidate.id)
            } else {
                break
            }
        }

        // Guarantee at least one weapon and one passive when both types are available
        val allWeapons = ownedWeapons + newWeapons
        val allPassives = ownedPassives + newPassives
        if (allWeapons.isNotEmpty() && allPassives.isNotEmpty()) {
            val hasWeapon = options.any { it.isWeapon && !it.isEvolution }
            val hasPassive = options.any { !it.isWeapon && !it.isFallback }

            if (!hasWeapon) {
                val replaceIdx = options.indexOfLast { !it.isWeapon && !it.isFallback }
                if (replaceIdx >= 0) {
                    val candidate = allWeapons.firstOrNull { !selected.contains(it.id) }
                    if (candidate != null) {
                        selected.remove(options[replaceIdx].id)
                        options[replaceIdx] = candidate
                        selected.add(candidate.id)
                    }
                }
            } else if (!hasPassive) {
                val replaceIdx = options.indexOfLast { it.isWeapon && !it.isEvolution }
                if (replaceIdx >= 0) {
                    val candidate = allPassives.firstOrNull { !selected.contains(it.id) }
                    if (candidate != null) {
                        selected.remove(options[replaceIdx].id)
                        options[replaceIdx] = candidate
                        selected.add(candidate.id)
                    }
                }
            }
        }

        // Guarantee at least one new item if available and grid has room
        if (newUpgrades.isNotEmpty()) {
            val hasNewInOptions = options.any { opt ->
                !opt.isEvolution && !opt.isFallback && newUpgrades.any { it.id == opt.id }
            }
            if (!hasNewInOptions) {
                val replaceIndex = options.indexOfLast { !it.isEvolution && !it.isFallback }
                if (replaceIndex >= 0) {
                    val newCandidate = newUpgrades.firstOrNull { !selected.contains(it.id) }
                    if (newCandidate != null) {
                        selected.remove(options[replaceIndex].id)
                        options[replaceIndex] = newCandidate
                        selected.add(newCandidate.id)
                    }
                }
            }
        }

        // Last resort — case 1: pool is completely empty
        if (options.isEmpty() && gatedWeapons.isNotEmpty()) {
            gatedWeapons.shuffle()
            options.addAll(gatedWeapons.take(GameConfig.UPGRADE_CHOICES))
        }

        // Last resort — case 2: restricted pool
        val onlyGatedWeaponsLeft = (ownedWeapons + newWeapons).isEmpty() && gatedWeapons.isNotEmpty()
        if (onlyGatedWeaponsLeft && options.none { it.isWeapon && !it.isEvolution }) {
            val promoted = gatedWeapons.shuffled().first()
            if (options.size < GameConfig.UPGRADE_CHOICES) {
                options.add(promoted)
            } else {
                val replaceIdx = options.indexOfLast { !it.isWeapon && !it.isFallback }
                if (replaceIdx >= 0) {
                    selected.remove(options[replaceIdx].id)
                    options[replaceIdx] = promoted
                }
            }
        }

        pendingOptions = options
        return options
    }

    fun getEligibleEvolutions(state: GameState): List<UpgradeOption> {
        if (state.astroLoopMode) {
            if (state.survivalTime < 480f || state.astroLoopEvolutionUsed) return emptyList()
        }
        val evolutions = mutableListOf<UpgradeOption>()
        for ((weaponId, level) in state.weaponLevels) {
            if (level >= 1) { // Eligible as long as weapon is active
                val weaponDef = WeaponDefinitions.getWeaponDef(weaponId) ?: continue
                val requiredPassive = weaponDef.evolutionPassive ?: continue
                val evolvedId = weaponDef.evolutionWeaponId ?: continue
                val hasRequiredPassive = when (requiredPassive) {
                    "tb26" -> state.getPassiveStacks("tb26") > 0 || state.getPassiveStacks("combat_drone") > 0
                    else   -> state.getPassiveStacks(requiredPassive) > 0
                }
                if (hasRequiredPassive && !state.hasEvolution(evolvedId)) {
                    evolutions.add(UpgradeOption(
                        id = evolvedId, isWeapon = true, isEvolution = true,
                        baseWeaponId = weaponId, requiredPassiveId = requiredPassive
                    ))
                }
            }
        }
        return evolutions
    }

    fun getEligibleLevelUps(state: GameState): List<UpgradeOption> {
        val eligible = mutableListOf<UpgradeOption>()
        for (weaponDef in WeaponDefinitions.getBaseWeapons()
            .filter { it.id in unlockedWeaponIds && !state.hasEvolutionOf(it.id) }) {
            val currentLevel = state.getWeaponLevel(weaponDef.id)
            if (currentLevel in 1..<GameConfig.WEAPON_MAX_LEVEL) {
                eligible.add(UpgradeOption(weaponDef.id, isWeapon = true))
            }
        }
        return eligible
    }

    fun generateEvolutionOptions(state: GameState): List<UpgradeOption> {
        val eligible = getEligibleEvolutions(state).shuffled()
        val options = eligible.take(3)
        pendingOptions = options
        return options
    }

    fun selectOption(index: Int): UpgradeOption? {
        if (index < 0 || index >= pendingOptions.size) return null
        val selected = pendingOptions[index]
        pendingOptions = emptyList()
        return selected
    }

    fun spawnPowerUp(x: Float, y: Float, state: GameState): PowerUp? {
        val dropChance = GameConfig.POWERUP_DROP_CHANCE * state.dropRateMultiplier *
            GameConfig.SALVAGE_BASE_RATE * state.getSalvageMultiplier()
        if (Random.nextFloat() > dropChance) return null

        val powerUp = powerUpPool.obtain()
        val isWeapon = Random.nextFloat() < 0.7f

        val itemId = if (isWeapon) {
            val validWeapons = WeaponDefinitions.getBaseWeapons().filter {
                it.id in unlockedWeaponIds && !state.hasEvolutionOf(it.id) &&
                state.getWeaponLevel(it.id) < GameConfig.WEAPON_MAX_LEVEL
            }
            validWeapons.randomOrNull()?.id
        } else {
            val validPassives = PassiveDefinitions.getAllPassives().filter { 
                it.id in unlockedPassiveIds &&
                state.getPassiveStacks(it.id) < GameConfig.PASSIVE_MAX_STACKS
                && !(it.id == "combat_drone" && state.activePilotId == PassiveDefinitions.ASTRO_PILOT_ID)
                && !(it.id == "tb26" && state.activePilotId != PassiveDefinitions.ASTRO_PILOT_ID) 
            }
            validPassives.randomOrNull()?.id
        }

        if (itemId == null) {
            powerUpPool.free(powerUp)
            return null
        }

        powerUp.initialize(
            x = x,
            y = y,
            powerUpType = if (isWeapon) PowerUpType.WEAPON else PowerUpType.PASSIVE,
            id = itemId
        )

        return powerUp
    }

    fun getPendingOptions(): List<UpgradeOption> = pendingOptions

    fun hasPendingOptions(): Boolean = pendingOptions.isNotEmpty()

    fun clearPendingOptions() {
        pendingOptions = emptyList()
    }

    fun generateWeaponOnlyOptions(state: GameState): List<UpgradeOption> {
        val options = mutableListOf<UpgradeOption>()
        val candidates = mutableListOf<UpgradeOption>()

        for (weaponDef in WeaponDefinitions.getBaseWeapons()
            .filter { it.id in unlockedWeaponIds && !state.hasEvolutionOf(it.id) }) {
            if (state.getWeaponLevel(weaponDef.id) < GameConfig.WEAPON_MAX_LEVEL) {
                candidates.add(UpgradeOption(weaponDef.id, isWeapon = true))
            }
        }

        candidates.shuffle()
        for (candidate in candidates) {
            if (options.size < GameConfig.UPGRADE_CHOICES) {
                options.add(candidate)
            }
        }

        pendingOptions = options
        return options
    }

    fun getAvailableUpgrades(state: GameState): List<UpgradeOption> {
        val available = mutableListOf<UpgradeOption>()
        val oneTimePassives = setOf("phoenix_core", "extra_weapon_slot", "glass_cannon", "duplicator_core", "lucky_star")

        for (weaponDef in WeaponDefinitions.getBaseWeapons()
            .filter { it.id in unlockedWeaponIds && !state.hasEvolutionOf(it.id) }) {
            val currentLevel = state.getWeaponLevel(weaponDef.id)
            if (currentLevel < GameConfig.WEAPON_MAX_LEVEL && (currentLevel > 0 || state.canAddNewWeapon())) {
                available.add(UpgradeOption(weaponDef.id, isWeapon = true))
            }
        }

        for (passiveDef in PassiveDefinitions.getAllPassives().filter { it.id in unlockedPassiveIds }) {
            val currentStacks = state.getPassiveStacks(passiveDef.id)

            if (currentStacks >= GameConfig.PASSIVE_MAX_STACKS) continue
            if (passiveDef.id in oneTimePassives && currentStacks > 0) continue
            if (passiveDef.id == "extra_weapon_slot" && state.hasExtraWeaponSlot) continue

            if (passiveDef.id == "combat_drone" && state.activePilotId == PassiveDefinitions.ASTRO_PILOT_ID) continue
            if (passiveDef.id == "tb26" && state.activePilotId != PassiveDefinitions.ASTRO_PILOT_ID) continue

            if (currentStacks > 0 || state.canAddNewPassive()) {
                available.add(UpgradeOption(passiveDef.id, isWeapon = false))
            }
        }

        return available
    }
}
