package com.vrgames.gravitysling.core

/**
 * All levels. Levels are "authored by trajectory": we pick the bodies and an intended shot, fly
 * it once, and drop the stars, checkpoints and goal onto that path. The intended shot therefore
 * always earns 3 stars, and LevelsTest proves it plus measures how forgiving it is.
 *
 * Playfield space: meters, origin about 1 m in front of the player at chest height,
 * +y up, -z away from the player. The launcher sits within arm's reach.
 */
object Levels {
    val LAUNCH = v(0.0, -0.15, 0.35)

    // ---- Shared bodies -------------------------------------------------------------------------
    private fun earth(pos: Vec3, r: Double = 0.2, mu: Double = 0.03) =
        BodySpec("earth", "地球", BodyKind.PLANET, pos, r, mu, look = "earth")

    private fun moon(pos: Vec3, r: Double = 0.08, mu: Double = 0.012, orbit: Orbit? = null) =
        BodySpec("moon", "月球", BodyKind.MOON, pos, r, mu, look = "moon", orbit = orbit)

    private fun ball(id: String, dock: Vec3, mu: Double = 0.02) =
        BodySpec(id, "引力球", BodyKind.GRAVITY_BALL, dock, 0.05, mu, look = "gravity_ball", movable = true)

    /** Optional ball: the level is solved without it, but placing it opens other routes. */
    private fun spareBall() = ball("ball1", v(0.6, -0.35, 0.25))

    private fun mars(pos: Vec3, r: Double = 0.15, mu: Double = 0.03) =
        BodySpec("mars", "火星", BodyKind.PLANET, pos, r, mu, look = "mars")

    private fun jupiter(pos: Vec3, r: Double, mu: Double) =
        BodySpec("jupiter", "木星", BodyKind.PLANET, pos, r, mu, look = "jupiter")

    /** A small moon on a circular orbit around [center]. [look] picks its tint. */
    private fun satellite(id: String, name: String, center: Vec3, r: Double, mu: Double, radius: Double, period: Double, phase: Double) =
        BodySpec(id, name, BodyKind.MOON, Vec3.ZERO, r, mu, look = id, orbit = Orbit(center, radius, period, phase))

    private fun booster(x: Double, y: Double, z: Double, factor: Double) = Booster(v(x, y, z), 0.07, factor)

    /** Deterministic pseudo-random in [0, 1); same formula as tools/levellab.py. */
    private fun hash01(k: Int): Double {
        val x = kotlin.math.sin(k * 12.9898 + 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }

    /**
     * Asteroid wall on the plane z = [planeZ] over an x/y grid, with a round gap of radius [gapR]
     * around [gap]. Mirrors levellab.belt() so designs made in Python match exactly.
     */
    private fun belt(planeZ: Double, gap: Pair<Double, Double>, gapR: Double, xs: List<Double>, ys: List<Double>, seed: Int, jitter: Double = 0.03): List<BodySpec> {
        val out = ArrayList<BodySpec>()
        var n = 0
        for (x in xs) for (y in ys) {
            val h = seed * 1000 + n * 3
            n++
            val px = x + (hash01(h) * 2 - 1) * jitter
            val py = y + (hash01(h + 1) * 2 - 1) * jitter
            val pz = planeZ + (hash01(h + 2) * 2 - 1) * jitter
            if (kotlin.math.hypot(px - gap.first, py - gap.second) < gapR) continue
            out.add(BodySpec("rock${out.size}", "陨石", BodyKind.ASTEROID, v(px, py, pz), 0.035, 0.0, look = "asteroid"))
        }
        return out
    }

    private fun frange(a: Double, b: Double, n: Int) = List(n) { a + (b - a) * it / (n - 1) }

    // ---- Chapter 1: 月港 (Earth–Moon) ----------------------------------------------------------
    val chapter1 = listOf(
        authored(
            id = "1-1", chapter = 1, number = 1, title = "首单",
            brief = "欢迎加入星驿快递！第一单很简单：把包裹送到前方的月港空间站。",
            tip = "捏住探测器往身前拉，像拉弹弓一样，松手发射。拉得越远，飞得越快。",
            bodies = listOf(earth(v(-0.8, -0.4, -0.3)), moon(v(0.35, 0.05, -0.55))),
            pull = v(0.02, -0.04, 0.17),
            goalTime = 3.2, starTimes = listOf(0.9, 1.7, 2.5),
            unlocks = listOf("slingshot", "earth", "cosmic_speed"),
        ),
        authored(
            id = "1-2", chapter = 1, number = 2, title = "月影",
            brief = "收件人在月球背后，直线飞过去会撞上月球。借月球的引力绕过去。",
            tip = "引力会让轨道弯曲：从月球旁边擦过，探测器就会被拉向月球后方。",
            bodies = listOf(earth(v(-0.85, -0.35, -0.1)), moon(v(0.0, 0.0, -0.55), r = 0.1, mu = 0.03)),
            pull = v(-0.08, 0.0, 0.12),
            goalTime = 3.7, starTimes = listOf(1.1, 2.0, 2.9),
            unlocks = listOf("moon", "lunar_sample"),
        ),
        authored(
            id = "1-3", chapter = 1, number = 3, title = "借力",
            brief = "今天公司给你配了一颗「引力球」。空间站在右前方，直着打会从它旁边飞过去：把引力球放到航线右边，让包裹拐个弯飞进站。",
            tip = "引力球和行星一样有引力，留在原位时不起作用。捏住右手边的紫球，拖到前方航线的右侧再松手，蓝色瞄准线会跟着弯过去。",
            bodies = listOf(
                earth(v(-0.85, -0.4, -0.2)),
                moon(v(0.45, -0.25, -0.75)),
                // Docked within easy reach; its spot is under half a meter away.
                ball("ball1", v(0.45, -0.3, 0.2), mu = 0.035),
            ),
            // A plain pull toward the player; the ball does the turning (found with the BallLab test).
            pull = v(0.0, -0.04, 0.17),
            placements = mapOf("ball1" to v(0.4, -0.1, -0.2)),
            goalTime = 2.4, starTimes = listOf(0.72, 1.44, 2.04),
            unlocks = listOf("gravity_ball", "sun"),
        ),
        authored(
            id = "1-4", chapter = 1, number = 4, title = "月背中继",
            brief = "月球背面收不到地球的信号。先飞过月背的中继点，再绕回来把包裹送到。",
            tip = "必须先穿过发光的中继环，空间站才会签收。",
            bodies = listOf(earth(v(-0.9, -0.35, 0.0)), moon(v(0.1, 0.0, -0.6), r = 0.1, mu = 0.05)),
            pull = v(0.023, -0.02, 0.07),
            goalTime = 6.28, starTimes = listOf(1.2, 2.6, 4.6), checkpointTimes = listOf(3.42),
            goalRadius = 0.09, checkpointRadius = 0.1,
            unlocks = listOf("relay"),
        ),
        authored(
            id = "1-5", chapter = 1, number = 5, title = "追月",
            brief = "月球在绕地球飞快地转。算好时机，从地月之间穿过去，冲向远处的转运站。",
            tip = "月球会一边走一边拉你。瞄准线已经算上了它的运动，跟着线走。",
            bodies = listOf(
                earth(v(0.0, -0.1, -0.55), r = 0.16, mu = 0.03),
                moon(
                    v(0.0, 0.0, 0.0), r = 0.07, mu = 0.02,
                    orbit = Orbit(center = v(0.0, -0.1, -0.55), radius = 0.42, period = 8.0, phaseDeg = 340.0),
                ),
            ),
            pull = v(-0.058, -0.028, 0.148),
            goalTime = 2.8, starTimes = listOf(0.7, 1.4, 2.1),
            unlocks = listOf("assist", "hohmann"),
        ),
        authored(
            id = "1-6", chapter = 1, number = 6, title = "追站", challenge = true,
            brief = "挑战：月港空间站正绕着地球飞，不会停下来等你。用引力球把航线弯到地球背后，在空间站经过的那一刻赶到。",
            tip = "空间站沿着淡蓝色的虚线圈飞，发射后才开始动。要瞄准它「将要到」的地方，而不是它现在的位置。",
            bodies = listOf(
                earth(v(0.0, -0.1, -0.6), r = 0.17, mu = 0.03),
                ball("ball1", v(0.6, -0.35, 0.25), mu = 0.025),
            ),
            pull = v(-0.065, -0.032, 0.099),
            placements = mapOf("ball1" to v(0.208, 0.198, -0.699)),
            goalTime = 3.25, starTimes = listOf(0.9, 1.8, 2.6), goalRadius = 0.085,
            goalAround = v(0.0, -0.1, -0.6), goalPeriod = -7.0,
            unlocks = listOf("station", "solar_sail"),
        ),
    )

    // ---- Chapter 2: 赤色航线 (Mars) -----------------------------------------------------------
    private val MARS_C3 = v(0.0, -0.05, -0.6)
    private val MARS_C5 = v(0.1, 0.0, -0.65)

    val chapter2 = listOf(
        authored(
            id = "2-1", chapter = 2, number = 1, title = "赤色门户",
            brief = "欢迎来到火星航线！前方是一片陨石带，只有一个缺口能穿过去。",
            tip = "撞上陨石和撞上星球一样会坠毁。先看清缺口在哪里，再拉弓。右手边的引力球可用可不用：把它放出来，能试出别的航线。",
            bodies = listOf(mars(v(0.5, -0.1, -0.75)), spareBall()) +
                belt(-0.42, -0.208 to 0.09, 0.13, frange(-0.7, 0.25, 7), frange(-0.45, 0.45, 6), seed = 3),
            pull = v(0.05, -0.05, 0.15),
            goalTime = 3.3, starTimes = listOf(0.9, 1.8, 2.6), goalRadius = 0.09,
            unlocks = listOf("mars", "probe_family", "mars_trio"),
        ),
        authored(
            id = "2-2", chapter = 2, number = 2, title = "加速环",
            brief = "这单很远，探测器的速度不够，会被火星拉下去。穿过金色的加速环，借一把力。",
            tip = "加速环能让速度提高八成。轻轻一拉，让探测器先钻过加速环。",
            bodies = listOf(mars(v(0.0, -0.05, -0.65), mu = 0.035)),
            boosters = listOf(booster(-0.041, -0.132, 0.25, 1.8)),
            pull = v(0.0326, 0.0092, 0.0624),
            goalTime = 3.57, starTimes = listOf(1.0, 2.0, 2.9), goalRadius = 0.1,
            unlocks = listOf("booster", "oberth"),
        ),
        authored(
            id = "2-3", chapter = 2, number = 3, title = "双卫",
            brief = "火星的两颗小卫星正在绕着它转，一快一慢、方向相反。从它们中间穿过去。",
            tip = "瞄准线已经算上了卫星的运动。拉弓时多看一会儿，等线从缝里穿过去再松手。",
            bodies = listOf(
                mars(MARS_C3, r = 0.14, mu = 0.03),
                satellite("phobos", "火卫一", MARS_C3, 0.05, 0.008, 0.3, 5.0, 120.0),
                satellite("deimos", "火卫二", MARS_C3, 0.045, 0.006, 0.47, -9.0, 210.0),
            ),
            pull = v(0.047, -0.054, 0.137),
            goalTime = 2.82, starTimes = listOf(0.85, 1.55, 2.25), goalRadius = 0.09,
            unlocks = listOf("mars_moons", "venus"),
        ),
        authored(
            id = "2-4", chapter = 2, number = 4, title = "穿针",
            brief = "陨石墙只在右下角留了一个小缺口，而空间站就藏在墙后面的正中间。",
            tip = "用引力球把轨道「勾」回来：先穿过缺口，再被引力球拉向空间站。",
            bodies = listOf(
                mars(v(-0.75, -0.25, -0.6)),
                ball("ball1", v(0.6, -0.35, 0.25), mu = 0.025),
            ) + belt(-0.45, 0.303 to -0.331, 0.12, frange(-0.45, 0.55, 7), frange(-0.55, 0.35, 6), seed = 4),
            pull = v(-0.065, 0.034, 0.126),
            placements = mapOf("ball1" to v(0.218, -0.258, -0.711)),
            goalTime = 3.33, starTimes = listOf(0.9, 1.8, 2.45), goalRadius = 0.09,
            unlocks = listOf("asteroid", "comet"),
        ),
        authored(
            id = "2-5", chapter = 2, number = 5, title = "火星快线",
            brief = "火星快线开通！加速、绕到火星背后经过中继环，再借火星的引力折返，把包裹送回近处的转运站。",
            tip = "这一单要飞近 7 秒。打开「瞄准线：完整」能看到整条航线。",
            bodies = listOf(
                mars(MARS_C5, r = 0.14, mu = 0.045),
                satellite("phobos", "火卫一", MARS_C5, 0.045, 0.006, 0.34, 7.0, 180.0),
            ),
            boosters = listOf(booster(0.082, -0.117, 0.267, 1.35)),
            pull = v(-0.0711, -0.0267, 0.065),
            goalTime = 6.875, starTimes = listOf(1.2, 2.6, 5.2), checkpointTimes = listOf(3.61),
            goalRadius = 0.12, checkpointRadius = 0.14,
            unlocks = listOf("window", "sample_return"),
        ),
        authored(
            id = "2-6", chapter = 2, number = 6, title = "弹弓失灵", challenge = true, maxPull = 0.1,
            brief = "挑战：发射架出了故障，只剩不到一半的力气，探测器飞不远。贴着火星擦过去，在离它最近、飞得最快的那一刻点火，把包裹甩到左上方的空间站。",
            tip = "不点火是送不到的。盯着橙色的点火航线，等它指向空间站时捏一下手指。",
            bodies = listOf(mars(v(0.05, -0.1, -0.45), r = 0.15, mu = 0.12)),
            pull = v(-0.076, 0.056, -0.007), burnAt = 2.69,
            goalTime = 4.75, starTimes = listOf(1.2, 2.4, 3.6), goalRadius = 0.1,
            unlocks = listOf("correction", "comet_chaser"),
        ),
    )

    // ---- Chapter 3: 深空邮路 (Jupiter) ---------------------------------------------------------
    private val JUP_C3 = v(0.0, -0.05, -0.7)
    private val JUP_C6 = v(-0.1, -0.05, -0.5)

    val chapter3 = listOf(
        authored(
            id = "3-1", chapter = 3, number = 1, title = "巨行星",
            brief = "木星的质量是地球的三百多倍。别正面冲过去，从它身边擦过，让它把你甩向背后的空间站。",
            tip = "木星引力很强，轻拉就够了。离得越近，转弯越急。右手边的引力球可用可不用：把它放出来，能试出别的航线。",
            bodies = listOf(jupiter(v(0.1, -0.05, -0.8), 0.24, 0.07), spareBall()),
            pull = v(-0.077, -0.051, 0.085),
            goalTime = 3.6, starTimes = listOf(1.0, 2.0, 2.9), goalRadius = 0.09,
            unlocks = listOf("jupiter", "jupiter_polar"),
        ),
        authored(
            id = "3-2", chapter = 3, number = 2, title = "虫洞",
            brief = "空间站被木星挡得严严实实。还好左手边有一个虫洞，它的出口在木星的右后方。",
            tip = "飞进紫色的虫洞，会从另一头的出口飞出来，速度和方向保持不变。",
            bodies = listOf(jupiter(v(0.05, -0.05, -0.75), 0.26, 0.07)),
            portals = listOf(Portal(Zone(v(-0.6, 0.05, 0.2), 0.08), v(0.95, 0.1, -1.0))),
            pull = v(0.124, -0.039, 0.006),
            goalTime = 4.6, starTimes = listOf(0.6, 1.3, 3.0), goalRadius = 0.09,
            unlocks = listOf("wormhole"),
        ),
        authored(
            id = "3-3", chapter = 3, number = 3, title = "伽利略卫星",
            brief = "木星的三颗大卫星排成了一条「旋转门」。找准时机，一口气穿过去。",
            tip = "卫星转得有快有慢。拉弓时盯住瞄准线，它会随着你的手实时变化。",
            bodies = listOf(
                jupiter(JUP_C3, 0.2, 0.05),
                satellite("io", "木卫一", JUP_C3, 0.04, 0.005, 0.33, 4.0, 330.0),
                satellite("europa", "木卫二", JUP_C3, 0.035, 0.004, 0.46, 7.0, 0.0),
                satellite("ganymede", "木卫三", JUP_C3, 0.05, 0.007, 0.6, 11.0, 150.0),
            ),
            pull = v(0.072, -0.044, 0.144),
            goalTime = 2.78, starTimes = listOf(0.8, 1.5, 2.2), goalRadius = 0.09,
            unlocks = listOf("galilean", "saturn"),
        ),
        authored(
            id = "3-4", chapter = 3, number = 4, title = "双球",
            brief = "公司这次给了你两颗引力球。用它们让包裹走一条 S 形航线。",
            tip = "一颗把轨道往左拉，一颗再往右拉回来。先放好，再看瞄准线微调。",
            bodies = listOf(
                jupiter(v(0.75, -0.25, -0.95), 0.2, 0.05),
                ball("ball1", v(-0.6, -0.4, 0.3), mu = 0.022),
                ball("ball2", v(0.6, -0.4, 0.3), mu = 0.022),
            ),
            pull = v(0.095, 0.027, 0.093),
            placements = mapOf("ball1" to v(-0.186, -0.19, -0.327), "ball2" to v(0.239, -0.056, -1.017)),
            goalTime = 3.55, starTimes = listOf(1.0, 2.0, 2.9), goalRadius = 0.08,
            unlocks = listOf("three_body", "ring_planet"),
        ),
        authored(
            id = "3-5", chapter = 3, number = 5, title = "回家",
            brief = "最后一单：从发射架加速，绕木星一大圈，经过木星背后的中继环，再钻进虫洞，把包裹送回你身边的星驿总部。",
            tip = "这是整条深空邮路。打开「瞄准线：完整」，慢慢找。",
            bodies = listOf(jupiter(v(0.1, -0.05, -0.8), 0.24, 0.07)),
            boosters = listOf(booster(0.002, -0.195, 0.297, 1.3)),
            portals = listOf(Portal(Zone(v(0.144, 0.378, -1.038), 0.12), v(0.6, 0.3, 0.0))),
            pull = v(-0.0014, 0.0472, 0.0505),
            goalTime = 5.93, starTimes = listOf(1.5, 3.0, 4.2), checkpointTimes = listOf(4.82),
            goalRadius = 0.11, checkpointRadius = 0.13,
            unlocks = listOf("deep_space", "grand_tour"),
        ),
        authored(
            id = "3-6", chapter = 3, number = 6, title = "近木点火", challenge = true, maxPull = 0.1,
            brief = "最后的挑战：发射架只剩一点力气，空间站却远在右边。先落向木星，贴着它掠过时点火，借木星给你的速度把自己甩出去。小心绕着木星转的木卫二。",
            tip = "离木星越近、飞得越快，点火越划算。橙色虚线会告诉你现在点火能飞到哪里。",
            bodies = listOf(
                jupiter(JUP_C6, 0.19, 0.15),
                satellite("europa", "木卫二", JUP_C6, 0.035, 0.004, 0.42, 6.0, 90.0),
            ),
            pull = v(0.072, 0.053, 0.040), burnAt = 0.87,
            goalTime = 5.75, starTimes = listOf(1.6, 3.0, 4.4), goalRadius = 0.1,
            unlocks = listOf("scale", "sun_dive", "pluto_flyby"),
        ),
    )

    val all: List<Level> = chapter1 + chapter2 + chapter3

    /** The 15 story levels, without the chapter challenges. */
    val main: List<Level> = all.filter { !it.challenge }

    val chapterTitles = mapOf(1 to "第一章 · 月港", 2 to "第二章 · 赤色航线", 3 to "第三章 · 深空邮路")

    fun byId(id: String) = all.first { it.id == id }

    // ---- Authoring helper ----------------------------------------------------------------------
    /**
     * Flies [pull] once (burning at [burnAt], if given) and places stars/checkpoints/goal on the
     * path at the given flight times. With [goalAround], the station circles that point with
     * [goalPeriod] and is timed to be at the goal spot exactly at [goalTime].
     */
    fun authored(
        id: String, chapter: Int, number: Int, title: String, brief: String, tip: String,
        bodies: List<BodySpec>, pull: Vec3, placements: Map<String, Vec3> = emptyMap(),
        goalTime: Double, starTimes: List<Double>, checkpointTimes: List<Double> = emptyList(),
        boosters: List<Booster> = emptyList(), portals: List<Portal> = emptyList(),
        goalRadius: Double = 0.075, checkpointRadius: Double = 0.08,
        launch: Vec3 = LAUNCH, unlocks: List<String> = emptyList(),
        burn: Boolean = chapter >= 2, burnAt: Double? = null, challenge: Boolean = false,
        maxPull: Double = Physics.MAX_PULL, goalAround: Vec3? = null, goalPeriod: Double = 0.0,
    ): Level {
        val draft = Level(
            id = id, chapter = chapter, number = number, title = title, brief = brief, tip = tip,
            launch = launch, bodies = bodies, goal = Zone(v(99.0, 99.0, 99.0), 0.0),
            boosters = boosters, portals = portals, timeLimit = goalTime + 1.0,
            burn = burn, challenge = challenge, maxPull = maxPull,
        )
        val f = Flight(draft, placements)
        f.launch(pull)
        val times = (starTimes + checkpointTimes + goalTime).sorted()
        val at = HashMap<Double, Vec3>()
        val events = ArrayList<FlightEvent>(4)
        var k = 0
        while (k < times.size) {
            check(f.status == FlightStatus.FLYING) { "level $id: intended shot ended (${f.status}) at t=${f.time}" }
            if (f.time >= times[k]) {
                at[times[k]] = f.position()
                k++
                continue
            }
            if (burnAt != null && f.time >= burnAt) f.burn()
            f.step(events)
        }
        val goalPos = at.getValue(goalTime)
        val orbit = goalAround?.let { orbitThrough(it, goalPos, goalPeriod, goalTime) }
        return draft.copy(
            goal = Zone(orbit?.positionAt(0.0) ?: goalPos, eased(goalRadius, chapter, challenge)),
            goalOrbit = orbit,
            stars = starTimes.map { at.getValue(it) },
            checkpoints = checkpointTimes.map { Zone(at.getValue(it), eased(checkpointRadius, chapter, challenge)) },
            timeLimit = goalTime + 6.0,
            unlocks = unlocks,
            solution = Solution(pull, placements, burnAt),
        )
    }

    /**
     * Station and relay catch radius after the difficulty ease (players found the authored sizes
     * too tight on the device): generous in chapter 1, a bit less after; challenges keep theirs.
     */
    private fun eased(radius: Double, chapter: Int, challenge: Boolean) = when {
        challenge -> radius
        chapter == 1 -> maxOf(radius * 2.0, 0.16)
        chapter == 2 -> radius * 1.5
        else -> radius * 1.25
    }

    /**
     * Circular orbit around [center] that passes [p] at time [t]. Its plane contains the line from
     * [center] to [p] and is tilted as little as possible from horizontal.
     */
    private fun orbitThrough(center: Vec3, p: Vec3, period: Double, t: Double): Orbit {
        val d = p - center
        val dn = d.normalized()
        val normal = (Vec3.UP - dn * Vec3.UP.dot(dn)).normalized()
        val base = Orbit(center, d.length(), period, 0.0, normal)
        val (u, w) = base.axes()
        val a = kotlin.math.atan2(d.dot(w), d.dot(u))
        return base.copy(phaseDeg = Math.toDegrees(a - 2 * kotlin.math.PI / period * t))
    }
}
