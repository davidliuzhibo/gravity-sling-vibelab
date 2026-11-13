package com.vrgames.gravitysling.core

/**
 * Encyclopedia cards unlocked by clearing levels. Real science and history are stated as plain
 * facts; no real mission names, logos or official imagery are used (see docs/知识产权.md).
 */
data class CodexCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val model: String,
    /** One-line highlight shown in the console when the card unlocks. */
    val fact: String,
    val shelf: Shelf = Shelf.IDEA,
    /** Key figures, shown as a row of labels over the text. */
    val stats: List<Pair<String, String>> = emptyList(),
    /** For a journey: the bodies passed in order (names in [Route.ORBITS]); drawn as a route that grows. */
    val route: List<String> = emptyList(),
)

/** The encyclopedia's three shelves. */
enum class Shelf(val title: String) { BODY("天体"), CRAFT("航天器与任务"), IDEA("原理") }

/** Where the route drawings put each body: distance from the sun on a squeezed scale (0..1) and angle. */
object Route {
    val ORBITS = mapOf(
        "太阳" to (0.0 to 0.0), "金星" to (0.16 to 200.0), "地球" to (0.24 to 0.0), "月球" to (0.24 to 12.0),
        "火星" to (0.33 to 300.0), "小行星" to (0.42 to 110.0), "木星" to (0.55 to 60.0), "土星" to (0.68 to 140.0),
        "天王星" to (0.79 to 230.0), "海王星" to (0.88 to 300.0), "冥王星" to (0.96 to 350.0), "彗星" to (0.45 to 250.0),
    )
}

object Codex {
    val cards = listOf(
        CodexCard(
            id = "slingshot", title = "小驿号", subtitle = "星驿快递 · 标准投递器",
            body = "小驿号是星驿快递的标准投递器（本作虚构）。它没有大推力发动机，" +
                "全靠发射架的「弹弓」起飞，再借天体引力改变航向。\n\n" +
                "真实的深空探测器也是这样精打细算：发射时给足速度，之后主要靠引力「滑行」，" +
                "只在关键时刻做一次小小的变轨。",
            model = "probe",
            fact = "真实的深空探测器大部分时间都在「滑行」，只在关键时刻点火。",
            shelf = Shelf.CRAFT,
            stats = listOf("类型" to "虚构投递器", "动力" to "发射架弹射"),
        ),
        CodexCard(
            id = "earth", title = "地球", subtitle = "我们的家园",
            body = "地球直径约 12742 公里，表面约 71% 被海水覆盖，是目前已知唯一有生命的星球。\n\n" +
                "地球的自转轴倾斜约 23.4 度，所以有了四季。地球的磁场像一把伞，挡住了太阳风里的大部分高能粒子；两极的极光，就是漏进来的粒子点亮了高层大气。",
            model = "earth",
            fact = "地球的磁场挡住了大部分太阳风，极光就是漏进来的粒子点亮的。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 12742 公里", "海洋" to "约 71%", "自转轴" to "倾斜 23.4°"),
        ),
        CodexCard(
            id = "moon", title = "月球", subtitle = "地球唯一的天然卫星",
            body = "月球的自转周期和绕地球公转的周期相同，所以它总是用同一面对着地球，" +
                "这叫「潮汐锁定」。从地球上永远看不到的那一面，就是月球背面。\n\n" +
                "2019 年 1 月，中国的探测器在月球背面实现软着陆，这是人类历史上的第一次。",
            model = "moon",
            fact = "月球总是用同一面对着地球，它的背面在地球上永远看不到。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 3474 公里", "距地球" to "约 38 万公里", "一面朝向" to "潮汐锁定"),
        ),
        CodexCard(
            id = "gravity_ball", title = "引力与轨道", subtitle = "为什么轨道会弯？",
            body = "牛顿设想过：在高山上水平发射炮弹，速度越快落得越远；快到一定程度，" +
                "炮弹「一直在往下掉，却永远掉不到地面」，这就是轨道。\n\n" +
                "引力的大小和距离的平方成反比：离天体近一倍，引力大四倍。所以擦边飞过时，" +
                "轨道弯得最厉害。",
            model = "gravity_ball",
            fact = "离天体近一倍，引力就大四倍，所以擦边飞过时轨道弯得最厉害。",
            shelf = Shelf.IDEA,
            stats = listOf("规律" to "平方反比", "近一倍" to "引力大四倍"),
        ),
        CodexCard(
            id = "relay", title = "中继卫星与 L2 点", subtitle = "怎样和月球背面通话？",
            body = "月球挡住了背面和地球之间的无线电信号。解决办法是在地月拉格朗日 L2 点附近" +
                "放一颗中继卫星：它在月球背后约 6.5 万公里处，既能看到月球背面，又能看到地球。\n\n" +
                "2018 年，中国发射了世界上第一颗运行在地月 L2 点附近的中继通信卫星，" +
                "为随后的月背着陆提供通信支持。",
            model = "station",
            fact = "中继卫星在月球背后约 6.5 万公里处，同时看得见月球背面和地球。",
            shelf = Shelf.IDEA,
            stats = listOf("位置" to "月背约 6.5 万公里", "首次" to "2018 年"),
        ),
        CodexCard(
            id = "assist", title = "引力助推", subtitle = "不花燃料的加速",
            body = "探测器从运动的行星旁边飞过时，会被行星「拖着走」一段，" +
                "离开时速度和方向都变了，相当于从行星的公转里借了一点能量。\n\n" +
                "这就是「引力弹弓」。很多飞向外太阳系的探测器都靠它连续加速，" +
                "用很少的燃料飞到几十亿公里之外。",
            model = "moon",
            fact = "很多飞向外太阳系的探测器，都是被行星一路「甩」出去的。",
            shelf = Shelf.IDEA,
            stats = listOf("燃料" to "几乎不花", "借的是" to "行星的公转"),
        ),
        CodexCard(
            id = "station", title = "空间站", subtitle = "一直在「往下掉」的实验室",
            body = "近地轨道上的空间站离地面约 400 公里，每秒飞行约 7.7 公里，大约 90 分钟绕地球一圈，所以站上的航天员一天能看到约 16 次日出。\n\n" +
                "那里的重力其实接近地面的九成。航天员飘起来，是因为空间站和他们一起在不停地「下落」，也就是牛顿大炮里的那种轨道运动。\n\n" +
                "想追上前面的空间站，飞船反而要先降到更低的轨道：轨道越低，绕一圈越快。",
            model = "station",
            fact = "空间站那里的重力接近地面的九成，航天员飘起来是因为在一起「下落」。",
            shelf = Shelf.CRAFT,
            stats = listOf("高度" to "约 400 公里", "速度" to "约 7.7 公里/秒", "一圈" to "约 90 分钟"),
        ),
        // ---- Chapter 2 ----
        CodexCard(
            id = "mars", title = "火星", subtitle = "红色星球",
            body = "火星表面的岩石和尘土里含有大量氧化铁，也就是铁锈，所以它看起来是红色的。" +
                "太阳系最高的火山奥林帕斯山就在火星上，高约 22 公里；水手号峡谷长约 4000 公里。\n\n" +
                "2021 年 5 月，中国的探测器在火星乌托邦平原南部着陆，随后火星车驶上了火星表面。",
            model = "mars",
            fact = "太阳系最高的火山在火星上，约 22 公里高，是珠峰的两倍多。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 6779 公里", "最高火山" to "约 22 公里", "一天" to "约 24.6 小时"),
        ),
        CodexCard(
            id = "probe_family", title = "探测器家族", subtitle = "飞掠、环绕、着陆、巡视、返回",
            body = "飞掠探测器从天体旁边飞过，边飞边拍；环绕探测器进入轨道，长期观测；着陆器在表面软着陆；巡视器（行星车）能在表面行驶；采样返回探测器还要把样品带回地球。\n\n" +
                "1977 年发射的一对飞掠探测器至今还在向外飞，其中一个在 2012 年飞出了太阳风吹到的范围，是离地球最远的人造物体。2020 年，中国的探测器从月球带回了 1731 克样品。",
            model = "probe",
            fact = "1977 年发射的一个飞掠探测器，如今是离地球最远的人造物体。",
            shelf = Shelf.CRAFT,
            stats = listOf("方式" to "飞掠·环绕·着陆·巡视·返回"),
        ),
        CodexCard(
            id = "booster", title = "变轨与速度增量", subtitle = "航天器怎样「换道」？",
            body = "航天器要改变轨道，就得改变速度。工程师用「速度增量」来计算一次任务需要多少燃料。\n\n" +
                "从地球轨道飞往火星，常用的是「霍曼转移」：先在合适的位置点火加速，进入一条椭圆轨道，" +
                "飞到目的地附近再点火一次。游戏里的加速环就相当于一次瞬间点火。",
            model = "booster",
            fact = "去火星最省燃料的轨道，一路上只需要点火两次。",
            shelf = Shelf.IDEA,
            stats = listOf("单位" to "公里/秒", "去火星" to "点火两次"),
        ),
        CodexCard(
            id = "mars_moons", title = "火卫一和火卫二", subtitle = "火星的两颗小卫星",
            body = "火卫一平均直径约 22 公里，不到 8 小时就绕火星一圈，比火星自转还快，" +
                "所以在火星上看，它是西升东落的。它正在慢慢靠近火星，几千万年后可能会被潮汐力撕碎。\n\n" +
                "火卫二更小、更远，约 30 小时绕一圈。\n\n" +
                "（游戏里为了增加挑战，让它们反向转动；真实的两颗卫星都朝同一个方向绕火星运行。）",
            model = "phobos",
            fact = "火卫一绕火星比火星自转还快，在火星上看它是西升东落的。",
            shelf = Shelf.BODY,
            stats = listOf("火卫一" to "约 22 公里", "绕一圈" to "不到 8 小时"),
        ),
        CodexCard(
            id = "asteroid", title = "小行星带", subtitle = "并没有电影里那么挤",
            body = "火星和木星之间有一条主小行星带，里面有上百万颗小行星，但它们加起来的质量还不到月球的百分之五。\n\n" +
                "小行星之间的平均距离有几十万甚至上百万公里，探测器穿过时几乎不可能撞上。" +
                "游戏里画得这么密集，是为了让关卡更好玩。",
            model = "asteroid",
            fact = "主小行星带里的小行星全部加起来，还不到月球质量的百分之五。",
            shelf = Shelf.BODY,
            stats = listOf("数量" to "上百万颗", "总质量" to "不到月球的 5%"),
        ),
        CodexCard(
            id = "window", title = "发射窗口", subtitle = "为什么去火星要等？",
            body = "地球和火星都在绕太阳转，要用最省燃料的轨道过去，两颗行星必须处在特定的相对位置。" +
                "这样的机会大约每 26 个月才出现一次，叫作「发射窗口」。\n\n" +
                "2020 年 7 月的窗口期里，中国、美国和阿联酋先后发射了各自的火星探测器。",
            model = "probe",
            fact = "去火星最省燃料的发射窗口，大约每 26 个月才出现一次。",
            shelf = Shelf.IDEA,
            stats = listOf("间隔" to "约 26 个月", "飞行" to "约 7 个月"),
        ),
        CodexCard(
            id = "correction", title = "轨道修正", subtitle = "点火的学问",
            body = "深空探测器在漫长的飞行途中，会做几次小小的「中途修正」：发动机点火几秒到几分钟，把航线微调回正轨。\n\n" +
                "点火的时机很有讲究：在离天体最近、速度最快的时候点火，同样多的燃料能换来最多的能量，这叫「奥伯特效应」。游戏里的点火也一样，在擦过行星的那一刻点火，效果最明显。",
            model = "booster",
            fact = "在离天体最近、飞得最快的时候点火最划算，这叫「奥伯特效应」。",
            shelf = Shelf.IDEA,
            stats = listOf("点火" to "几秒到几分钟", "最划算" to "近天体处"),
        ),
        // ---- Chapter 3 ----
        CodexCard(
            id = "jupiter", title = "木星", subtitle = "行星之王",
            body = "木星是太阳系最大的行星，质量约是地球的 318 倍，比其他所有行星加起来的两倍还多。" +
                "它自转一圈不到 10 小时，是自转最快的行星。\n\n" +
                "木星南半球的大红斑是一场比地球还大的风暴，人类已经观测它一百多年了。",
            model = "jupiter",
            fact = "木星自转一圈不到 10 小时，是自转最快的行星。",
            shelf = Shelf.BODY,
            stats = listOf("质量" to "约 318 个地球", "一天" to "不到 10 小时", "大红斑" to "比地球大"),
        ),
        CodexCard(
            id = "wormhole", title = "虫洞", subtitle = "本作唯一的「科幻」元素",
            body = "1935 年，爱因斯坦和罗森在广义相对论的方程里发现了一种可以连接时空中两个遥远地点的结构，" +
                "后来被称为「虫洞」。\n\n" +
                "到目前为止，虫洞只存在于理论中，从来没有被观测到。即使存在，要让它保持打开，" +
                "可能还需要一种具有「负能量」的奇异物质。游戏里的虫洞纯属想象。",
            model = "wormhole",
            fact = "虫洞只存在于理论中，至今从来没有被观测到。",
            shelf = Shelf.IDEA,
            stats = listOf("提出" to "1935 年", "观测到" to "从未"),
        ),
        CodexCard(
            id = "galilean", title = "伽利略卫星", subtitle = "1610 年的伟大发现",
            body = "1610 年，伽利略用自制的望远镜发现了木星的四颗大卫星，证明了并不是所有天体都绕着地球转。\n\n" +
                "木卫一是太阳系火山活动最剧烈的天体；木卫二的冰壳下面很可能有一片液态海洋；" +
                "木卫三是太阳系最大的卫星，比水星还大。（游戏里只出现了其中三颗。）",
            model = "io",
            fact = "木卫三比水星还大，是太阳系最大的卫星。",
            shelf = Shelf.BODY,
            stats = listOf("发现" to "1610 年", "最大" to "木卫三，比水星大"),
        ),
        CodexCard(
            id = "three_body", title = "三体问题", subtitle = "为什么轨道这么难算？",
            body = "两个天体在引力作用下怎么运动，可以用公式精确求出来。可是只要再加一个天体，" +
                "一般就没有通用的公式了，长期的运动甚至可能是混沌的，初始位置差一点点，结果就差很远。\n\n" +
                "游戏里的天体都按固定的轨道运动，只有探测器受引力影响，这在天文学里叫「限制性」问题。" +
                "即便如此，也只能一步一步地数值计算，你看到的瞄准线就是这样算出来的。",
            model = "gravity_ball",
            fact = "只要有三个天体互相吸引，一般就没有通用的公式能算出它们怎么动。",
            shelf = Shelf.IDEA,
            stats = listOf("两个天体" to "有公式", "三个天体" to "一般没有"),
        ),
        CodexCard(
            id = "deep_space", title = "深空通信", subtitle = "来自远方的回信",
            body = "无线电信号以光速传播，但宇宙实在太大：从火星发回地球，单程要 3 到 22 分钟；从木星发回来，要 30 到 50 多分钟。\n\n" +
                "所以深空探测器必须能自己处理很多事情。地面上则要用口径几十米的大天线，" +
                "才能收到那些非常微弱的信号。\n\n" +
                "恭喜你完成了星驿快递的全部航线！",
            model = "station",
            fact = "从木星发回地球的无线电信号，要在路上走半个多小时。",
            shelf = Shelf.IDEA,
            stats = listOf("火星→地球" to "3–22 分钟", "木星→地球" to "30–50 分钟"),
        ),
        CodexCard(
            id = "scale", title = "真实比例", subtitle = "宇宙到底有多空旷？",
            body = "为了能在房间里玩，游戏把距离缩短了很多。如果把地球缩成游戏里这么大（直径约 40 厘米）：\n\n" +
                "月球只有 11 厘米大，却在 12 米之外；火星离我们最近时也在约 1.8 公里外；木星有 4.4 米高，在约 18 公里外；太阳是一个 44 米高的大球，在约 4.7 公里外。\n\n" +
                "光从月球到地球要 1.3 秒，从太阳到地球要 8 分多钟。",
            model = "earth",
            fact = "按游戏里地球的大小，月球应该在 12 米外，太阳在 4.7 公里外。",
            shelf = Shelf.IDEA,
            stats = listOf("按游戏比例" to "月球在 12 米外", "太阳" to "4.7 公里外"),
        ),
        // ---- 2026-11-12: more of the solar system, real journeys (told without mission names, see
        // docs/知识产权.md) and the ideas behind them. Unlocked across the levels (core/Levels.kt).
        CodexCard(
            id = "sun", title = "太阳", subtitle = "太阳系的中心",
            body = "太阳的直径约 139 万公里，能装下一百多万个地球；它的质量占了整个太阳系的 99.8% 以上，所有行星都被它的引力牵着转。\n\n" +
                "太阳表面约 5500 摄氏度，中心约 1500 万摄氏度，靠核聚变发光发热。阳光从太阳走到地球，要 8 分 20 秒左右。",
            model = "sun",
            fact = "太阳的质量占整个太阳系的 99.8% 以上。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 139 万公里", "表面" to "约 5500℃", "光到地球" to "约 8 分 20 秒"),
        ),
        CodexCard(
            id = "venus", title = "金星", subtitle = "最热的行星，也是借力的好帮手",
            body = "金星大小和地球差不多，却被浓厚的二氧化碳大气包着，地面气压约是地球的 92 倍，温度约 465 摄氏度，比离太阳更近的水星还热。\n\n" +
                "金星自转很慢，而且方向和大多数行星相反：它自转一圈要 243 个地球日，比它绕太阳一圈（225 天）还长。飞往太阳和外行星的探测器，常常先找金星借力。",
            model = "venus",
            fact = "金星上的一天比它的一年还长。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 12104 公里", "表面" to "约 465℃", "自转" to "243 天（逆向）"),
        ),
        CodexCard(
            id = "saturn", title = "土星", subtitle = "戴着冰环的行星",
            body = "土星是太阳系第二大的行星，平均密度比水还小。它那圈明亮的环主要由大大小小的冰块组成，宽约 28 万公里，大部分地方却只有几十米厚。\n\n" +
                "土星已知的卫星超过一百四十颗，其中最大的土卫六有浓厚的大气和液态甲烷湖。",
            model = "saturn",
            fact = "土星环宽约 28 万公里，大部分地方却只有几十米厚。",
            shelf = Shelf.BODY,
            stats = listOf("直径" to "约 12 万公里", "密度" to "比水小", "卫星" to "超过 140 颗"),
        ),
        CodexCard(
            id = "comet", title = "彗星", subtitle = "会长尾巴的「脏雪球」",
            body = "彗星的核是冰和尘埃冻在一起的「脏雪球」，通常只有几公里大。它们大多来自海王星以外的寒冷地带。\n\n" +
                "彗星靠近太阳时，冰受热直接变成气体，带着尘埃散开，形成明亮的彗发和长长的彗尾。彗尾总是背向太阳，最长可以超过一亿公里。",
            model = "comet",
            fact = "彗尾总是背向太阳，不管彗星往哪个方向飞。",
            shelf = Shelf.BODY,
            stats = listOf("彗核" to "通常几公里", "成分" to "冰和尘埃", "彗尾" to "可长过一亿公里"),
        ),
        CodexCard(
            id = "grand_tour", title = "行星接力", subtitle = "1977 年的外行星之旅",
            body = "1977 年，两艘外行星探测器先后发射。那几年木星、土星、天王星、海王星恰好排成了约 175 年才有一次的队列，探测器可以从一颗行星「甩」到下一颗。\n\n" +
                "其中一艘借着四颗行星依次加速，是至今唯一近距离拜访过天王星和海王星的探测器。另一艘在 2012 年飞出了太阳风吹到的范围，如今离地球已超过 240 亿公里。",
            model = "dish_probe",
            fact = "约 175 年一遇的行星队列，让一艘探测器连访了四颗外行星。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "1977 年", "借力" to "4 颗行星", "如今" to "离地球超过 240 亿公里"),
            route = listOf("地球", "木星", "土星", "天王星", "海王星"),
        ),
        CodexCard(
            id = "ring_planet", title = "土星轨道器", subtitle = "绕了一大圈才出发",
            body = "1997 年发射的一艘土星探测器太重，火箭没法直接把它送到土星。工程师给它设计了一条「绕远路」的航线：先两次借金星，再借一次地球，最后借木星，飞了近 7 年才到土星。\n\n" +
                "它绕着土星工作了 13 年，还放下一个着陆器降落在土卫六上。2017 年，它按计划冲进土星大气，结束了任务。",
            model = "dish_probe",
            fact = "为了到土星，它先借了两次金星、一次地球、一次木星。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "1997 年", "借力" to "4 次", "飞行" to "近 7 年"),
            route = listOf("地球", "金星", "金星", "地球", "木星", "土星"),
        ),
        CodexCard(
            id = "pluto_flyby", title = "冥王星飞掠", subtitle = "九年半的长跑",
            body = "2006 年 1 月发射的一艘探测器，离开地球时的速度是有史以来最快的。2007 年它擦过木星，借木星的引力又加了速，把去冥王星的路程缩短了约三年。\n\n" +
                "2015 年 7 月，它从冥王星旁边飞过，第一次拍下了冥王星表面那块心形的冰原。",
            model = "dish_probe",
            fact = "借木星的一次加速，让它早了约三年到达冥王星。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2006 年", "借木星" to "省约 3 年", "到达" to "2015 年"),
            route = listOf("地球", "木星", "冥王星"),
        ),
        CodexCard(
            id = "sun_dive", title = "贴近太阳", subtitle = "要减速才能靠近",
            body = "飞向太阳反而很难：地球带着探测器绕太阳飞得很快，要靠近太阳，得把这份速度「减掉」。2018 年发射的一艘太阳探测器，就是靠七次飞掠金星一点点减速，越绕越靠近太阳。\n\n" +
                "它的隔热罩朝着太阳一面要承受一千多摄氏度的高温。在离太阳最近时，它的速度约每秒 190 多公里，是人类造出的最快的物体。",
            model = "sun_probe",
            fact = "它借金星七次，是为了「减速」好靠近太阳。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2018 年", "借金星" to "7 次", "最快" to "约 192 公里/秒"),
            route = listOf("地球", "金星", "金星", "金星", "太阳"),
        ),
        CodexCard(
            id = "comet_chaser", title = "追彗星", subtitle = "十年追上一颗彗星",
            body = "2004 年发射的一艘探测器要追上一颗彗星，可彗星飞得太快了。它先后三次借地球、一次借火星，花了十年才追上。\n\n" +
                "2014 年，它成为第一艘绕着彗星飞行的探测器，还放下一个小着陆器，第一次降落在彗核上。",
            model = "wing_probe",
            fact = "它借了三次地球、一次火星，花十年才追上彗星。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2004 年", "借力" to "4 次", "飞行" to "10 年"),
            route = listOf("地球", "地球", "火星", "地球", "地球", "彗星"),
        ),
        CodexCard(
            id = "jupiter_polar", title = "木星极轨", subtitle = "靠太阳能飞到木星",
            body = "2011 年发射的一艘木星探测器，先绕太阳飞了一大圈，2013 年回到地球附近借力加速，2016 年进入绕木星南北两极的轨道。\n\n" +
                "木星离太阳很远，阳光只有地球附近的约 1/25，它却是第一艘只靠太阳能电池在木星工作的探测器，三片太阳翼又大又长。",
            model = "wing_probe",
            fact = "木星那里的阳光只有地球的约 1/25，它仍然靠太阳能工作。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2011 年", "借地球" to "1 次", "到达" to "2016 年"),
            route = listOf("地球", "地球", "木星"),
        ),
        CodexCard(
            id = "sample_return", title = "小行星采样", subtitle = "往返五十多亿公里",
            body = "2014 年发射的一艘小行星探测器，2015 年借地球引力加速，2018 年到达一颗直径约 900 米的小行星。它在小行星上取了样，还打出一个人工坑，采集了地下的物质。\n\n" +
                "2020 年 12 月，它把装着样品的返回舱送回地球，一路往返约 52 亿公里。",
            model = "capsule",
            fact = "为了一小盒小行星样品，它往返了约 52 亿公里。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2014 年", "往返" to "约 52 亿公里", "送回样品" to "2020 年"),
            route = listOf("地球", "地球", "小行星", "地球"),
        ),
        CodexCard(
            id = "mars_trio", title = "绕、着、巡", subtitle = "一次完成三件事",
            body = "2020 年 7 月，中国的火星探测器在发射窗口出发，飞了约 7 个月到达火星。\n\n" +
                "2021 年，它一次完成了「绕、着、巡」三步：环绕器留在轨道上，着陆器在乌托邦平原南部降落，火星车开到地面巡视探测。第一次去火星就同时做到这三件事，在世界上是首次。",
            model = "rover",
            fact = "第一次去火星，就一次完成了环绕、着陆和巡视。",
            shelf = Shelf.CRAFT,
            stats = listOf("发射" to "2020 年 7 月", "飞行" to "约 7 个月", "完成" to "绕·着·巡"),
            route = listOf("地球", "火星"),
        ),
        CodexCard(
            id = "lunar_sample", title = "月球采样返回", subtitle = "带回 1731 克月壤",
            body = "2020 年 11 月底，中国的探测器飞向月球，在月面钻取和铲取样品后从月面起飞，在月球轨道上和返回器对接，再把样品送回地球。\n\n" +
                "整个任务只用了约 23 天，带回 1731 克月壤，这是人类时隔四十多年再次从月球带回样品。",
            model = "capsule",
            fact = "约 23 天往返，带回了 1731 克月壤。",
            shelf = Shelf.CRAFT,
            stats = listOf("时间" to "2020 年", "样品" to "1731 克", "全程" to "约 23 天"),
            route = listOf("地球", "月球", "地球"),
        ),
        CodexCard(
            id = "hohmann", title = "霍曼转移", subtitle = "最省燃料的换道",
            body = "1925 年，德国工程师霍曼算出：在两个圆形轨道之间，最省燃料的走法是一条椭圆。先在内侧轨道点火，让椭圆的远端正好碰到外侧轨道，到了那里再点火一次。\n\n" +
                "照这个办法从地球飞火星，要飞约 8 个半月，而且两颗行星的位置要刚好对上，这就是「发射窗口」的由来。",
            model = "booster",
            fact = "地球到火星最省燃料的椭圆航线，要飞约 8 个半月。",
            stats = listOf("提出" to "1925 年", "点火" to "两次", "去火星" to "约 8.5 个月"),
            route = listOf("地球", "火星"),
        ),
        CodexCard(
            id = "cosmic_speed", title = "三个宇宙速度", subtitle = "多快才能离开？",
            body = "从地面出发：每秒 7.9 公里就能绕着地球转，不再掉下来，这是第一宇宙速度；每秒 11.2 公里能挣脱地球，去别的行星，这是第二宇宙速度；每秒 16.7 公里能挣脱太阳，飞出太阳系，这是第三宇宙速度。\n\n" +
                "火箭很难一次达到第三宇宙速度，所以飞往太阳系外的探测器，都要请行星帮忙「甩」一把。",
            model = "probe",
            fact = "每秒 16.7 公里，才能从地球出发飞出太阳系。",
            stats = listOf("绕地球" to "7.9 公里/秒", "离开地球" to "11.2 公里/秒", "离开太阳系" to "16.7 公里/秒"),
        ),
        CodexCard(
            id = "oberth", title = "奥伯特效应", subtitle = "在最快的时候点火",
            body = "同样烧掉一份燃料，在飞得越快的时候点火，换来的能量越多。探测器在掠过天体、离它最近的那一刻飞得最快，这时点火最划算。\n\n" +
                "这个道理是火箭先驱奥伯特提出的。游戏第二章起的「飞行中点火」，就是在擦过行星时捏一下最有效。",
            model = "booster",
            fact = "同样的燃料，在飞得最快的时候烧，换来的能量最多。",
            stats = listOf("最佳时机" to "离天体最近时", "游戏里" to "擦过行星时点火"),
        ),
        CodexCard(
            id = "solar_sail", title = "太阳帆", subtitle = "被阳光推着走",
            body = "光虽然没有重量，却带着动量。一大片极薄的反光膜对着太阳，就会被阳光轻轻地推着走，一点燃料也不用。推力非常小，但能一直推下去，越飞越快。\n\n" +
                "2010 年，一艘太阳帆探测器在飞往金星的途中展开了一张厚度只有约 7.5 微米的帆，第一次在行星际空间证明了阳光能推着航天器前进。",
            model = "sail",
            fact = "阳光能推着一张薄帆一直加速，一点燃料都不用。",
            stats = listOf("帆厚" to "约 7.5 微米", "燃料" to "不用", "首次成功" to "2010 年"),
        ),
    )

    fun byId(id: String) = cards.firstOrNull { it.id == id }

    fun onShelf(shelf: Shelf) = cards.filter { it.shelf == shelf }
}
