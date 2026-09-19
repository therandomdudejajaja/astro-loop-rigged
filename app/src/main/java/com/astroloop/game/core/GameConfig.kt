package com.astroloop.game.core

object GameConfig {
    // Display
    const val TARGET_FPS = 120
    const val FRAME_TIME_MS = 1000L / TARGET_FPS

    // Design resolution — all logical coordinates target this size
    const val DESIGN_WIDTH = 960f
    const val DESIGN_HEIGHT = 2142f

    // Ship
    const val SHIP_BASE_SPEED = 600f          // Doubled movement speed
    const val SHIP_BASE_HEALTH = 10000f        // Massive health pool
    const val SHIP_BASE_SHIELDS = 10000f       // Massive shield capacity
    const val SHIELD_REGEN_RATE = 5000.0f      // Ultra-fast shield regeneration
    const val SHIELD_REGEN_DELAY = 0f          // Instant shield regen with no delay
    const val SHIP_BASE_SIZE = 25f
    const val SHIP_ACCELERATION = 2000f        // Snappier responsiveness
    const val SHIP_DECELERATION = 1000f
    const val SHIP_INVULNERABILITY_TIME = 3.0f

    // Joystick
    const val JOYSTICK_DEAD_ZONE = 20f
    const val JOYSTICK_MAX_RADIUS = 120f

    // Asteroids
    const val ASTEROID_BASE_SPEED = 80f
    const val ASTEROID_LARGE_SIZE = 50f
    const val ASTEROID_MEDIUM_SIZE = 30f
    const val ASTEROID_SMALL_SIZE = 15f
    const val ASTEROID_SPAWN_MARGIN = 100f
    const val ASTEROID_INITIAL_SPAWN_RATE = 2f
    const val ASTEROID_MIN_SPAWN_RATE = 0.3f

    // Power-ups & Economy
    const val POWERUP_DROP_CHANCE = 1.0f       // Guaranteed powerup drop chance
    const val YEN_BASE_RATE = 100.0f           // 100x Currency earnings
    const val SALVAGE_BASE_RATE = 100.0f       // 100x Salvage earnings
    const val POWERUP_SIZE = 20f
    const val POWERUP_MAGNET_BASE_RANGE = 2000f// Screen-wide magnet radius
    const val POWERUP_COLLECT_RANGE = 100f     // Instant collection range
    const val POWERUP_PULL_SPEED = 1500f       // Super fast item pull speed

    // Phoenix Core shockwave
    const val PHOENIX_SHOCKWAVE_MAX_RADIUS = 1500f
    const val PHOENIX_SHOCKWAVE_DURATION = 0.6f

    // Boss charge sequence
    const val BOSS_CHARGE_DURATION = 62f
    const val BOSS_EMP_CHARGE_THRESHOLD = 0.45f
    const val BOSS_CHARGED_SHOT_DAMAGE = 10000f

    // Boss EMP rush-in
    const val BOSS_RUSH_TRIGGER_DISTANCE = 300f
    const val BOSS_RUSH_SPEED_FLOOR = 700f
    const val BOSS_RUSH_MAX_CLOSE_TIME = 3f
    const val BOSS_RUSH_EASE_DURATION = 0.5f
    const val BOSS_RUSH_BRAKE_DURATION = 0.3f
    const val BOSS_RUSH_ARRIVAL_BEAT = 0.4f

    // Difficulty scaling
    const val DIFFICULTY_SPAWN_RATE_INCREASE = 0.15f
    const val DIFFICULTY_SPEED_INCREASE = 0.15f
    const val ASTEROID_MAX_SPEED_FACTOR = 7.0f
    const val DIFFICULTY_HEALTH_INCREASE = 0.05f

    // Weapons & Passives (Infinite potential)
    const val WEAPON_MAX_LEVEL = 999999
    const val PASSIVE_MAX_STACKS = 999999

    // Upgrade slots
    const val MAX_WEAPON_SLOTS = 12
    const val MAX_PASSIVE_SLOTS = 12
    const val PASSIVE_SLOTS_WITH_EXTRA_WEAPON = 12

    // Upgrade selection
    const val UPGRADE_CHOICES = 6               // Show 6 upgrade choices per roll

    // Starfield
    const val STARS_FAR_COUNT = 50
    const val STARS_MID_COUNT = 30
    const val STARS_NEAR_COUNT = 15
    const val STARS_FAR_SPEED_FACTOR = 0.2f
    const val STARS_MID_SPEED_FACTOR = 0.5f
    const val STARS_NEAR_SPEED_FACTOR = 1.0f

    // Colors
    const val COLOR_SHIP = 0xFF00FF00.toInt()
    const val COLOR_ASTEROID = 0xFFFFFFFF.toInt()
    const val COLOR_ASTEROID_ICE = 0xFF88CCFF.toInt()
    const val COLOR_ASTEROID_METAL = 0xFFAAAAAA.toInt()
    const val COLOR_ASTEROID_VOLATILE = 0xFFFF8844.toInt()
    const val COLOR_ASTEROID_MAGNETIC = 0xFFFF44FF.toInt()
    const val COLOR_ASTEROID_TRAIL = 0xFF44FF44.toInt()
    const val COLOR_PROJECTILE = 0xFFFFFF00.toInt()
    const val COLOR_POWERUP = 0xFF00FFFF.toInt()
    const val COLOR_HUD = 0xFFFFFFFF.toInt()
    const val COLOR_HEALTH_BAR = 0xFF00FF00.toInt()
    const val COLOR_HEALTH_BAR_BG = 0xFF333333.toInt()
    const val COLOR_BACKGROUND = 0xFF000011.toInt()
    const val COLOR_STAR_FAR = 0xFF444444.toInt()
    const val COLOR_STAR_MID = 0xFF888888.toInt()
    const val COLOR_STAR_NEAR = 0xFFCCCCCC.toInt()

    // Time thresholds for unlocking asteroid types
    const val UNLOCK_ICE_ASTEROIDS = 60f
    const val UNLOCK_METAL_ASTEROIDS = 120f
    const val UNLOCK_VOLATILE_ASTEROIDS = 180f
    const val UNLOCK_MAGNETIC_ASTEROIDS = 240f
    const val UNLOCK_TRAIL_ASTEROIDS = 300f

    // Enemy ships
    const val ENEMY_SPAWN_INTERVAL = 120f
    const val ENEMY_SPAWN_DISTANCE = 600f
    const val ENEMY_DESPAWN_DISTANCE = 2000f

    // Camera/world bounds
    const val ENTITY_DESPAWN_DISTANCE = 1500f

    // Critical hits
    const val CRIT_CHANCE_PER_LEVEL = 0.25f   // 25% crit chance per level
    const val CRIT_DAMAGE_MULTIPLIER = 10.0f  // 10x damage on critical hits

    // Upgrade drop rate limits (Cooldowns removed for rapid drops)
    const val ASTEROID_UPGRADE_DROP_COOLDOWN = 0f
    const val ASTEROID_UPGRADE_EARLY_COOLDOWN = 0f

    // Early game upgrade drop scaling
    const val ASTEROID_UPGRADE_DROP_INITIAL = 1.0f
    const val ASTEROID_UPGRADE_DROP_BASELINE = 1.0f
    const val ASTEROID_UPGRADE_DROP_DECREASE = 0f

    // Astro Loop mode drop rates
    const val ASTRO_LOOP_UPGRADE_DROP_COOLDOWN = 0f
    const val ASTRO_LOOP_UPGRADE_EARLY_COOLDOWN = 0f
    const val ASTRO_LOOP_UPGRADE_DROP_INITIAL = 1.0f
    const val ASTRO_LOOP_UPGRADE_DROP_BASELINE = 1.0f

    fun formatYen(amount: Int): String {
        return when {
            amount >= 1_000_000 -> {
                if (amount % 1_000_000 == 0) "${amount / 1_000_000}M"
                else "${String.format("%.1f", amount / 1_000_000f)}M"
            }
            amount >= 100_000 -> {
                val k = amount / 1_000
                if (amount % 1_000 == 0) "${k}K"
                else "${String.format("%.1f", amount / 1_000f)}K"
            }
            amount >= 10_000 -> {
                val k = amount / 1_000
                if (amount % 1_000 == 0) "¥${k}K"
                else "¥${String.format("%.1f", amount / 1_000f)}K"
            }
            else -> "¥$amount"
        }
    }
}
