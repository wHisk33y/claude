package de.knuffi.core

import kotlinx.serialization.Serializable

/** The overall look of the app, chosen at the start. All content stays available for everyone. */
@Serializable
enum class LookStyle(val title: String, val emoji: String, val description: String) {
    ZAUBER("Zauber", "🦄", "Rosa und lila, mit Einhörnern, Feen und Glitzer."),
    ABENTEUER("Abenteuer", "🐉", "Blau und grün, mit Drachen, Dinos und Robotern."),
}

@Serializable
enum class Stage(val title: String, val minLevel: Int) {
    EGG("Ei", 0),
    BABY("Baby", 1),
    CHILD("Kind", 3),
    TEEN("Teenager", 7),
    ADULT("Erwachsen", 13),

    // Every ten levels the pet changes once more.
    PRAECHTIG("Prächtig", 20),
    ERFAHREN("Erfahren", 30),
    WEISE("Weise", 40),
    EHRWUERDIG("Ehrwürdig", 50),
    URALT("Uralt", 60),
    ZEITLOS("Zeitlos", 70),
}

/** Egg types. Every line has its own family tree of creatures. */
@Serializable
enum class EggLine(
    val title: String,
    val emoji: String,
    val description: String,
    val look: LookStyle? = null,
    val eventOnly: Boolean = false,
    /** Real animals (cats and small animals) come in a basket and are born as babies. */
    val real: Boolean = false,
) {
    KNUFFEL("Knuffel-Ei", "🥚", "Das klassische Ei. Was schlüpft, hängt ganz von deiner Pflege ab."),
    WALD("Wald-Ei", "🌿", "Riecht nach Moos und Tannennadeln."),
    MEER("Meeres-Ei", "🌊", "Wenn man es ans Ohr hält, rauscht das Meer."),
    FEUER("Feuer-Ei", "🔥", "Ganz schön warm! Drinnen knistert es.", LookStyle.ABENTEUER),
    URZEIT("Urzeit-Ei", "🦖", "Ein uraltes Ei mit dicker, gepunkteter Schale.", LookStyle.ABENTEUER),
    TECHNO("Techno-Ei", "🤖", "Blinkt und piept geheimnisvoll.", LookStyle.ABENTEUER),
    EINHORN("Regenbogen-Ei", "🦄", "Schimmert in allen Farben des Regenbogens.", LookStyle.ZAUBER),
    BLUETE("Blüten-Ei", "🌸", "Duftet nach Frühlingsblumen.", LookStyle.ZAUBER),
    STERN("Sternen-Ei", "✨", "Funkelt wie der Nachthimmel."),
    ZUCKER("Zucker-Ei", "🍭", "Riecht nach Zuckerwatte und Karamell."),
    DSCHUNGEL("Dschungel-Ei", "🌴", "Grün gesprenkelt. Drinnen raschelt und zirpt es."),
    WUESTE("Wüsten-Ei", "🏜️", "Warm wie Sand in der Sonne. Riecht nach Abenteuer."),
    EDELSTEIN("Edelstein-Ei", "💎", "Funkelt wie ein frisch geschliffener Kristall."),
    FROST("Frost-Ei", "❄️", "Eiskalt! Gibt es nur zum Winterzauber.", eventOnly = true),
    GRUSEL("Grusel-Ei", "🎃", "Huuuh! Gibt es nur zu Halloween.", eventOnly = true),

    /** Real cats for the cat mode. Not an egg but a basket with a kitten in it. */
    KATZE("Katzenkörbchen", "🧺", "Ein kuscheliges Körbchen. Darin schläft ein kleines Kätzchen.", real = true),

    /** Small animals, also real and in a basket. */
    HASE("Hasenkörbchen", "🐰", "Ein Körbchen voller Heu. Darin kuschelt ein kleines Häschen.", real = true),
    HAMSTER("Hamsterhäuschen", "🐹", "Ein kleines Holzhäuschen. Darin raschelt ein Hamsterbaby.", real = true),
    MEERI("Meerschweinchen-Häuschen", "🌾", "Ein Häuschen mit frischem Heu. Drinnen quiekt es leise.", real = true),
    ;

    /** All creatures (without the egg) that can hatch from this line. */
    val forms: List<Form> get() = Form.entries.filter { it.line == this && it != Form.EGG }

    /** Fantasy egg lines (everything but the real animals). */
    val fantasy: Boolean get() = !real

    /** Hase, Hamster and Meerschweinchen. */
    val small: Boolean get() = this == HASE || this == HAMSTER || this == MEERI
}

/** The place of a form in its family tree. */
@Serializable
enum class Role {
    EGG,
    BABY,
    CHILD,
    CHILD_B,
    TEEN,
    TEEN_B,
    TEEN_SPORTY,
    TEEN_FOODIE,
    ADULT_SPORTY,
    ADULT_FOODIE,
    ADULT_BALANCED,
    SHADOW,
    LEGEND,

    // Older forms from level 20 on: A is the active path, B the cozy one.
    S20_A, S20_B, S30_A, S30_B, S40_A, S40_B, S50_A, S50_B, S60_A, S60_B, S70_A, S70_B,
    ;

    /** Belongs to the cozy path (or leads there when nothing else decides). */
    val cozy: Boolean get() = name.endsWith("_B") || this == ADULT_FOODIE || this == SHADOW

    companion object {
        /** The role of the older form at [stage] on the cozy or the active path. */
        fun elder(stage: Stage, cozy: Boolean): Role? =
            if (stage.ordinal <= Stage.ADULT.ordinal) null else valueOf("S${stage.minLevel}_${if (cozy) "B" else "A"}")
    }
}

@Serializable
enum class Form(
    val stage: Stage,
    val title: String,
    val description: String,
    val line: EggLine = EggLine.KNUFFEL,
    val role: Role,
) {
    EGG(Stage.EGG, "Ei", "Etwas bewegt sich darin …", role = Role.EGG),

    // Knuffel (the original tree)
    BABY(Stage.BABY, "Knuffel", "Ein winziges, flauschiges Wesen.", role = Role.BABY),
    HOPSI(Stage.CHILD, "Hopsi", "Fröhlich und verspielt, mit langen Hüpfohren.", role = Role.CHILD),
    GRUMMEL(Stage.CHILD, "Grummel", "Etwas stachelig … aber mit gutem Herz.", role = Role.CHILD_B),
    FLITZER(Stage.TEEN, "Flitzer", "Sportlich und immer in Bewegung.", role = Role.TEEN_SPORTY),
    MAMPFI(Stage.TEEN, "Mampfi", "Rund, gemütlich und immer hungrig.", role = Role.TEEN_FOODIE),
    LUMI(Stage.TEEN, "Lumi", "Ausgeglichen, mit leuchtender Antenne.", role = Role.TEEN),
    STACHLI(Stage.TEEN, "Stachli", "Launisch und stachelig.", role = Role.TEEN_B),
    DRAKO(Stage.ADULT, "Drako", "Ein stolzer kleiner Drache.", role = Role.ADULT_SPORTY),
    STELLARIS(Stage.ADULT, "Stellaris", "Ein Sternenwesen voller Magie.", role = Role.ADULT_BALANCED),
    MOCHI_KOENIG(Stage.ADULT, "Mochi-König", "Rund, weich und königlich.", role = Role.ADULT_FOODIE),
    SCHATTLING(Stage.ADULT, "Schattling", "Ein kleiner Geist: vernachlässigt, aber treu.", role = Role.SHADOW),
    AURELIUS(Stage.ADULT, "Aurelius", "Ein goldener Knuffel mit Krone und Heiligenschein. Legendär!", role = Role.LEGEND),

    // Wald
    MOOSI(Stage.BABY, "Moosi", "Ein weiches Moosbällchen mit Kleeblatt.", EggLine.WALD, Role.BABY),
    ZWEIGI(Stage.CHILD, "Zweigi", "Ein kleiner Waldgeist mit Blätter-Ohren.", EggLine.WALD, Role.CHILD),
    FARNI(Stage.TEEN, "Farni", "Flink wie ein Eichhörnchen, mit buschigem Schweif.", EggLine.WALD, Role.TEEN),
    KNORRI(Stage.TEEN, "Knorri", "Knorrig und brummelig wie eine alte Wurzel.", EggLine.WALD, Role.TEEN_B),
    HIRSCHLING(Stage.ADULT, "Hirschling", "Ein edler Waldhirsch mit Geweih.", EggLine.WALD, Role.ADULT_SPORTY),
    PILZBAER(Stage.ADULT, "Pilzbär", "Ein gemütlicher Bär mit rotem Pilzhut.", EggLine.WALD, Role.ADULT_FOODIE),
    WALDHUETER(Stage.ADULT, "Waldhüter", "Hütet den Wald. Auf seinem Kopf wächst eine Blume.", EggLine.WALD, Role.ADULT_BALANCED),
    WELTENBAUM(Stage.ADULT, "Weltenbaum", "Uralt und weise. In seinem Geweih blühen Sterne. Legendär!", EggLine.WALD, Role.LEGEND),

    // Meer
    BLUBB(Stage.BABY, "Blubb", "Ein Wassertropfen mit Kulleraugen.", EggLine.MEER, Role.BABY),
    FLOSSI(Stage.CHILD, "Flossi", "Planscht am liebsten den ganzen Tag.", EggLine.MEER, Role.CHILD),
    WELLI(Stage.TEEN, "Welli", "Surft auf jeder Welle.", EggLine.MEER, Role.TEEN),
    KRABBO(Stage.TEEN, "Krabbo", "Etwas zwickig, mit harter Schale.", EggLine.MEER, Role.TEEN_B),
    DELFINO(Stage.ADULT, "Delfino", "Blitzschnell und immer zu Späßen aufgelegt.", EggLine.MEER, Role.ADULT_SPORTY),
    WALBERT(Stage.ADULT, "Walbert", "Ein kugelrunder kleiner Wal.", EggLine.MEER, Role.ADULT_FOODIE),
    MEERLI(Stage.ADULT, "Meerli", "Ein Meereswesen mit schimmernder Flosse.", EggLine.MEER, Role.ADULT_BALANCED),
    AQUARION(Stage.ADULT, "Aquarion", "Hüter der Tiefsee mit Perlenkrone. Legendär!", EggLine.MEER, Role.LEGEND),

    // Feuer
    FUNKI(Stage.BABY, "Funki", "Ein kleiner, warmer Funke.", EggLine.FEUER, Role.BABY),
    GLUTI(Stage.CHILD, "Gluti", "Mit einer Flamme auf dem Kopf und voller Energie.", EggLine.FEUER, Role.CHILD),
    FLAMMO(Stage.TEEN, "Flammo", "Ein junger Drache mit Stummelflügeln.", EggLine.FEUER, Role.TEEN),
    ASCHO(Stage.TEEN, "Ascho", "Qualmt ein bisschen, wenn er schlechte Laune hat.", EggLine.FEUER, Role.TEEN_B),
    PYRO(Stage.ADULT, "Pyro", "Ein flinker Feuerdrache.", EggLine.FEUER, Role.ADULT_SPORTY),
    MAGMO(Stage.ADULT, "Magmo", "Kugelrund und warm wie ein Ofen.", EggLine.FEUER, Role.ADULT_FOODIE),
    SALAMANDO(Stage.ADULT, "Salamando", "Ein eleganter Feuersalamander.", EggLine.FEUER, Role.ADULT_BALANCED),
    PHOENIX(Stage.ADULT, "Phönix", "Steigt strahlend aus der Glut empor. Legendär!", EggLine.FEUER, Role.LEGEND),

    // Urzeit
    DINOLINO(Stage.BABY, "Dinolino", "Ein Mini-Dino, der noch ein Stück Schale trägt.", EggLine.URZEIT, Role.BABY),
    RAPTI(Stage.CHILD, "Rapti", "Neugierig und schnell auf zwei Beinen.", EggLine.URZEIT, Role.CHILD),
    TRIKI(Stage.TEEN, "Triki", "Hat drei kleine Hörner und einen Nackenschild.", EggLine.URZEIT, Role.TEEN),
    STEGI(Stage.TEEN, "Stegi", "Ein Dickkopf mit Rückenplatten.", EggLine.URZEIT, Role.TEEN_B),
    REXI(Stage.ADULT, "Rexi", "Ein kleiner T-Rex mit großem Brüllen.", EggLine.URZEIT, Role.ADULT_SPORTY),
    BRONTI(Stage.ADULT, "Bronti", "Frisst am liebsten Baumkronen.", EggLine.URZEIT, Role.ADULT_FOODIE),
    PTERO(Stage.ADULT, "Ptero", "Segelt über urzeitliche Wälder.", EggLine.URZEIT, Role.ADULT_BALANCED),
    URZEITKOENIG(Stage.ADULT, "Urzeit-König", "Der König der Dinos, mit goldenen Platten. Legendär!", EggLine.URZEIT, Role.LEGEND),

    // Techno
    BIEPI(Stage.BABY, "Biepi", "Ein kleiner Roboter, der fröhlich piept.", EggLine.TECHNO, Role.BABY),
    SCHRAUBI(Stage.CHILD, "Schraubi", "Baut aus allem etwas Neues.", EggLine.TECHNO, Role.CHILD),
    BLITZBOT(Stage.TEEN, "Blitzbot", "Flink und elektrisch geladen.", EggLine.TECHNO, Role.TEEN),
    ROSTI(Stage.TEEN, "Rosti", "Quietscht ein bisschen und braucht Öl.", EggLine.TECHNO, Role.TEEN_B),
    RAKETI(Stage.ADULT, "Raketi", "Ein Roboter mit Raketenrucksack.", EggLine.TECHNO, Role.ADULT_SPORTY),
    MAMPFBOT(Stage.ADULT, "Mampfbot", "Ein Koch-Roboter mit Kochmütze.", EggLine.TECHNO, Role.ADULT_FOODIE),
    ROBORITTER(Stage.ADULT, "Roboritter", "Ein freundlicher Roboter-Ritter.", EggLine.TECHNO, Role.ADULT_BALANCED),
    CHROMDRACHE(Stage.ADULT, "Chromdrache", "Ein Roboterdrache aus schimmerndem Chrom. Legendär!", EggLine.TECHNO, Role.LEGEND),

    // Einhorn
    PONYCHEN(Stage.BABY, "Ponychen", "Ein Fohlen mit winzigem Horn.", EggLine.EINHORN, Role.BABY),
    GLITZI(Stage.CHILD, "Glitzi", "Hinterlässt überall ein bisschen Glitzer.", EggLine.EINHORN, Role.CHILD),
    REGENBOGENHUF(Stage.TEEN, "Regenbogenhuf", "Läuft auf Regenbögen.", EggLine.EINHORN, Role.TEEN),
    WOLKI(Stage.TEEN, "Wolki", "Ein launisches Wolkenpony.", EggLine.EINHORN, Role.TEEN_B),
    PEGASUS(Stage.ADULT, "Pegasus", "Fliegt mit Federflügeln über den Himmel.", EggLine.EINHORN, Role.ADULT_SPORTY),
    ZUCKERHORN(Stage.ADULT, "Zuckerhorn", "Liebt Zuckerwatte über alles.", EggLine.EINHORN, Role.ADULT_FOODIE),
    STERNHORN(Stage.ADULT, "Sternhorn", "Ein echtes Einhorn mit Regenbogenmähne.", EggLine.EINHORN, Role.ADULT_BALANCED),
    AURORA(Stage.ADULT, "Aurora", "Ein geflügeltes Einhorn mit Nordlicht-Mähne. Legendär!", EggLine.EINHORN, Role.LEGEND),

    // Blüte
    KNOSPI(Stage.BABY, "Knospi", "Eine kleine Blütenknospe.", EggLine.BLUETE, Role.BABY),
    BLUEMI(Stage.CHILD, "Blümi", "Trägt eine Blume als Hut.", EggLine.BLUETE, Role.CHILD),
    FEELI(Stage.TEEN, "Feeli", "Eine kleine Fee mit Schmetterlingsflügeln.", EggLine.BLUETE, Role.TEEN),
    DORNI(Stage.TEEN, "Dorni", "Hat ein paar Dornen, aber ein weiches Herz.", EggLine.BLUETE, Role.TEEN_B),
    LIBELLA(Stage.ADULT, "Libella", "Schwirrt wie eine Libelle durch die Luft.", EggLine.BLUETE, Role.ADULT_SPORTY),
    HUMMELCHEN(Stage.ADULT, "Hummelchen", "Eine flauschige Hummel, die Honig liebt.", EggLine.BLUETE, Role.ADULT_FOODIE),
    ROSALIE(Stage.ADULT, "Rosalie", "Die Blütenfee. Wo sie hinfliegt, blüht es.", EggLine.BLUETE, Role.ADULT_BALANCED),
    TITANIA(Stage.ADULT, "Titania", "Die Königin aller Blüten. Legendär!", EggLine.BLUETE, Role.LEGEND),

    // Stern
    STERNCHEN(Stage.BABY, "Sternchen", "Ein kleiner Stern, der vom Himmel fiel.", EggLine.STERN, Role.BABY),
    KOMETI(Stage.CHILD, "Kometi", "Zieht einen funkelnden Schweif hinter sich her.", EggLine.STERN, Role.CHILD),
    MONDI(Stage.TEEN, "Mondi", "Schläft am Tag und strahlt in der Nacht.", EggLine.STERN, Role.TEEN),
    NEBULI(Stage.TEEN, "Nebuli", "Ein kleiner Sternennebel, etwas durcheinander.", EggLine.STERN, Role.TEEN_B),
    ASTRO(Stage.ADULT, "Astro", "Ein Astronaut mit Glashelm.", EggLine.STERN, Role.ADULT_SPORTY),
    SATURNO(Stage.ADULT, "Saturno", "Kugelrund und mit eigenem Ring.", EggLine.STERN, Role.ADULT_FOODIE),
    GALAXIA(Stage.ADULT, "Galaxia", "Trägt eine ganze Galaxie im Bauch.", EggLine.STERN, Role.ADULT_BALANCED),
    SOLARIS(Stage.ADULT, "Solaris", "Leuchtet heller als die Sonne. Legendär!", EggLine.STERN, Role.LEGEND),

    // Zucker
    BONBONI(Stage.BABY, "Bonboni", "Ein kleines, rundes Bonbon.", EggLine.ZUCKER, Role.BABY),
    LOLLI(Stage.CHILD, "Lolli", "Mit einem Lolli-Wirbel auf dem Kopf.", EggLine.ZUCKER, Role.CHILD),
    GUMMIBAER(Stage.TEEN, "Gummibär", "Ein Gummibärchen, das lustig wackelt.", EggLine.ZUCKER, Role.TEEN),
    LAKRITZI(Stage.TEEN, "Lakritzi", "Schwarz und ein kleines bisschen bitter.", EggLine.ZUCKER, Role.TEEN_B),
    POPCORNI(Stage.ADULT, "Popcorni", "Hüpft wie Popcorn im Topf.", EggLine.ZUCKER, Role.ADULT_SPORTY),
    TORTELLA(Stage.ADULT, "Tortella", "Eine Torte mit Kirsche obendrauf.", EggLine.ZUCKER, Role.ADULT_FOODIE),
    MAKRONI(Stage.ADULT, "Makroni", "Eine zarte Macaron-Prinzessin.", EggLine.ZUCKER, Role.ADULT_BALANCED),
    KARAMELLA(Stage.ADULT, "Karamella", "Die Königin von Candyland. Legendär!", EggLine.ZUCKER, Role.LEGEND),

    // Frost (Winterzauber)
    FLOECKCHEN(Stage.BABY, "Flöckchen", "Eine Schneeflocke, die nicht schmelzen will.", EggLine.FROST, Role.BABY),
    PINGI(Stage.CHILD, "Pingi", "Ein kleiner Pinguin, der gern rutscht.", EggLine.FROST, Role.CHILD),
    SCHNEEHASE(Stage.TEEN, "Schneehase", "Schneeweiß und flauschig.", EggLine.FROST, Role.TEEN),
    EISZAPFI(Stage.TEEN, "Eiszapfi", "Stachelig wie ein Eiszapfen.", EggLine.FROST, Role.TEEN_B),
    POLARIX(Stage.ADULT, "Polarix", "Ein flinker kleiner Eisbär.", EggLine.FROST, Role.ADULT_SPORTY),
    SCHNEEMO(Stage.ADULT, "Schneemo", "Ein runder Schneemann mit Karottennase.", EggLine.FROST, Role.ADULT_FOODIE),
    POLARFUCHS(Stage.ADULT, "Polarfuchs", "Ein eleganter Fuchs mit weißem Fell.", EggLine.FROST, Role.ADULT_BALANCED),
    BOREAS(Stage.ADULT, "Boreas", "Herrscher über Schnee und Nordlicht. Legendär!", EggLine.FROST, Role.LEGEND),

    // Grusel (Halloween)
    KUERBI(Stage.BABY, "Kürbi", "Ein kleiner Kürbis mit Gesicht.", EggLine.GRUSEL, Role.BABY),
    GEISTI(Stage.CHILD, "Geisti", "Ein Mini-Gespenst, das gern Huhu ruft.", EggLine.GRUSEL, Role.CHILD),
    FLEDDI(Stage.TEEN, "Fleddi", "Eine kleine Fledermaus, die nachts herumflattert.", EggLine.GRUSEL, Role.TEEN),
    MUMMI(Stage.TEEN, "Mummi", "Ganz in Bandagen gewickelt.", EggLine.GRUSEL, Role.TEEN_B),
    VAMPI(Stage.ADULT, "Vampi", "Ein kleiner Vampir, ganz harmlos.", EggLine.GRUSEL, Role.ADULT_SPORTY),
    SCHLEIMI(Stage.ADULT, "Schleimi", "Ein glibberiger, gefräßiger Schleim.", EggLine.GRUSEL, Role.ADULT_FOODIE),
    HEXKATZE(Stage.ADULT, "Hexkatze", "Eine schwarze Katze mit Hexenhut.", EggLine.GRUSEL, Role.ADULT_BALANCED),
    KUERBISKOENIG(Stage.ADULT, "Kürbiskönig", "Der König der Halloween-Nacht. Legendär!", EggLine.GRUSEL, Role.LEGEND),

    // Echte Katze (Katzen-Modus)
    KAETZCHEN(Stage.BABY, "Kätzchen", "Winzig, tapsig und unendlich neugierig.", EggLine.KATZE, Role.BABY),
    JUNGKATZE(Stage.CHILD, "Jungkatze", "Jagt alles, was sich bewegt, am liebsten Wollknäuel.", EggLine.KATZE, Role.CHILD),
    WILDFANG(Stage.CHILD, "Wildfang", "Ein bisschen kratzbürstig, aber eigentlich verschmust.", EggLine.KATZE, Role.CHILD_B),
    HALBSTARKE(Stage.TEEN, "Halbstarke Katze", "Lange Beine, große Pläne.", EggLine.KATZE, Role.TEEN),
    MOTZKATZE(Stage.TEEN, "Motzkatze", "Faucht gern mal, wenn ihr etwas nicht passt.", EggLine.KATZE, Role.TEEN_B),
    MAEUSEJAEGER(Stage.ADULT, "Mäusejägerin", "Schlank, schnell und immer auf der Lauer.", EggLine.KATZE, Role.ADULT_SPORTY),
    PUMMELKATZE(Stage.ADULT, "Pummelkatze", "Rund, gemütlich und ein echter Feinschmecker.", EggLine.KATZE, Role.ADULT_FOODIE),
    SCHMUSEKATZE(Stage.ADULT, "Schmusekatze", "Schnurrt, sobald man sie nur anschaut.", EggLine.KATZE, Role.ADULT_BALANCED),
    STREUNER(Stage.ADULT, "Streunerkatze", "Etwas zerzaust, aber treu wie Gold.", EggLine.KATZE, Role.SHADOW),
    GLUECKSKATZE(Stage.ADULT, "Glückskatze", "Seidiges Fell und ein Blick voller Weisheit. Legendär!", EggLine.KATZE, Role.LEGEND),

    // Dschungel
    LIANI(Stage.BABY, "Liani", "Ein kleines Blätterknäuel mit einem Rankenschwänzchen.", EggLine.DSCHUNGEL, Role.BABY),
    KOKOSI(Stage.CHILD, "Kokosi", "Trägt eine halbe Kokosnuss als Helm.", EggLine.DSCHUNGEL, Role.CHILD),
    TUKANI(Stage.TEEN, "Tukani", "Ein bunter Tukan mit riesigem Schnabel.", EggLine.DSCHUNGEL, Role.TEEN),
    SCHLINGI(Stage.TEEN, "Schlingi", "Ein grummeliges Chamäleon, das ständig die Farbe wechselt.", EggLine.DSCHUNGEL, Role.TEEN_B),
    JAGUARO(Stage.ADULT, "Jaguaro", "Ein flinker Dschungelkater mit Flecken.", EggLine.DSCHUNGEL, Role.ADULT_SPORTY),
    BANANO(Stage.ADULT, "Banano", "Ein gemütlicher kleiner Gorilla, der Bananen liebt.", EggLine.DSCHUNGEL, Role.ADULT_FOODIE),
    ORCHIDEA(Stage.ADULT, "Orchidea", "Ein Paradiesvogel mit einer Orchideenblüte.", EggLine.DSCHUNGEL, Role.ADULT_BALANCED),
    SMARAGDLOEWE(Stage.ADULT, "Smaragdlöwe", "Ein Löwe mit grüner Mähne, König des Dschungels. Legendär!", EggLine.DSCHUNGEL, Role.LEGEND),

    // Wüste
    SANDI(Stage.BABY, "Sandi", "Ein warmes Sandkörnchen mit großen Augen.", EggLine.WUESTE, Role.BABY),
    FENNI(Stage.CHILD, "Fenni", "Ein Wüstenfuchs mit riesigen Ohren.", EggLine.WUESTE, Role.CHILD),
    SKARABI(Stage.TEEN, "Skarabi", "Ein glänzender Käfer mit Panzer.", EggLine.WUESTE, Role.TEEN),
    KAKTUSI(Stage.TEEN, "Kaktusi", "Stachelig und ein bisschen launisch.", EggLine.WUESTE, Role.TEEN_B),
    WIRBELI(Stage.ADULT, "Wirbeli", "Ein Sandsturm-Wesen, schneller als der Wind.", EggLine.WUESTE, Role.ADULT_SPORTY),
    KAMELIO(Stage.ADULT, "Kamelio", "Ein rundes Kamel, das nie Durst hat.", EggLine.WUESTE, Role.ADULT_FOODIE),
    SPHINXI(Stage.ADULT, "Sphinxi", "Eine weise kleine Sphinx, die Rätsel liebt.", EggLine.WUESTE, Role.ADULT_BALANCED),
    PHARAONI(Stage.ADULT, "Pharaoni", "Der goldene Herrscher der Dünen. Legendär!", EggLine.WUESTE, Role.LEGEND),

    // Edelstein
    KRISTALLI(Stage.BABY, "Kristalli", "Ein winziger, funkelnder Kristall.", EggLine.EDELSTEIN, Role.BABY),
    RUBINI(Stage.CHILD, "Rubini", "Rot wie ein Rubin und warmherzig.", EggLine.EDELSTEIN, Role.CHILD),
    SAPHIRO(Stage.TEEN, "Saphiro", "Blau und klar wie Bergwasser.", EggLine.EDELSTEIN, Role.TEEN),
    GEODI(Stage.TEEN, "Geodi", "Außen grau und rau, innen voller Glitzer.", EggLine.EDELSTEIN, Role.TEEN_B),
    DIAMANTO(Stage.ADULT, "Diamanto", "Ein flinker Drache aus Diamant.", EggLine.EDELSTEIN, Role.ADULT_SPORTY),
    BERNSTEINI(Stage.ADULT, "Bernsteini", "Ein runder Bär aus warmem Bernstein.", EggLine.EDELSTEIN, Role.ADULT_FOODIE),
    OPALIA(Stage.ADULT, "Opalia", "Schimmert in allen Farben des Regenbogens.", EggLine.EDELSTEIN, Role.ADULT_BALANCED),
    PRISMARA(Stage.ADULT, "Prismara", "Ein Einhorn aus Licht, das alle Farben zeigt. Legendär!", EggLine.EDELSTEIN, Role.LEGEND),

    // Echte Hasen
    HAESCHEN(Stage.BABY, "Häschen", "Winzig, mit weichen Öhrchen und einem Stummelschwänzchen.", EggLine.HASE, Role.BABY),
    JUNGHASE(Stage.CHILD, "Junghase", "Hoppelt den ganzen Tag herum.", EggLine.HASE, Role.CHILD),
    HOPPELHASE(Stage.TEEN, "Hoppelhase", "Springt über alles, was im Weg steht.", EggLine.HASE, Role.TEEN),
    TROTZHASE(Stage.TEEN, "Trotzhase", "Klopft mit dem Fuß, wenn ihm etwas nicht passt.", EggLine.HASE, Role.TEEN_B),
    SPRINTHASE(Stage.ADULT, "Sprinthase", "Der schnellste Hase weit und breit.", EggLine.HASE, Role.ADULT_SPORTY),
    PUMMELHASE(Stage.ADULT, "Pummelhase", "Rund und gemütlich, am liebsten mit einer Möhre.", EggLine.HASE, Role.ADULT_FOODIE),
    KUSCHELHASE(Stage.ADULT, "Kuschelhase", "Lässt sich stundenlang kraulen.", EggLine.HASE, Role.ADULT_BALANCED),
    ZAUSELHASE(Stage.ADULT, "Zauselhase", "Etwas zerzaust, aber mit großem Herz.", EggLine.HASE, Role.SHADOW),
    MONDHASE(Stage.ADULT, "Mondhase", "Silbernes Fell, das im Mondlicht glänzt. Legendär!", EggLine.HASE, Role.LEGEND),

    // Echte Hamster
    HAMSTERBABY(Stage.BABY, "Hamsterbaby", "Klein wie ein Daumen und furchtbar neugierig.", EggLine.HAMSTER, Role.BABY),
    JUNGHAMSTER(Stage.CHILD, "Junghamster", "Rennt im Laufrad, bis ihm schwindelig wird.", EggLine.HAMSTER, Role.CHILD),
    FLITZHAMSTER(Stage.TEEN, "Flitzhamster", "Flink wie der Blitz.", EggLine.HAMSTER, Role.TEEN),
    GRUMMELHAMSTER(Stage.TEEN, "Grummelhamster", "Murmelt vor sich hin, wenn er gestört wird.", EggLine.HAMSTER, Role.TEEN_B),
    LAUFRADPROFI(Stage.ADULT, "Laufradprofi", "Läuft jede Nacht einen Marathon.", EggLine.HAMSTER, Role.ADULT_SPORTY),
    BACKENHAMSTER(Stage.ADULT, "Backenhamster", "Stopft sich die Backen bis zum Platzen voll.", EggLine.HAMSTER, Role.ADULT_FOODIE),
    KUSCHELHAMSTER(Stage.ADULT, "Kuschelhamster", "Schläft am liebsten in deiner Hand.", EggLine.HAMSTER, Role.ADULT_BALANCED),
    ZAUSELHAMSTER(Stage.ADULT, "Zauselhamster", "Etwas strubbelig, aber treu.", EggLine.HAMSTER, Role.SHADOW),
    STERNENHAMSTER(Stage.ADULT, "Sternenhamster", "Sein Fell glitzert wie der Nachthimmel. Legendär!", EggLine.HAMSTER, Role.LEGEND),

    // Echte Meerschweinchen
    QUIEKCHEN(Stage.BABY, "Quiekchen", "Ein winziges Meerschweinchen, das fröhlich quiekt.", EggLine.MEERI, Role.BABY),
    JUNGSCHWEINCHEN(Stage.CHILD, "Jungschweinchen", "Knabbert an allem, was grün ist.", EggLine.MEERI, Role.CHILD),
    WIRBELSCHWEINCHEN(Stage.TEEN, "Wirbelschweinchen", "Hat lustige Haarwirbel im Fell.", EggLine.MEERI, Role.TEEN),
    BRUMMSCHWEINCHEN(Stage.TEEN, "Brummschweinchen", "Brummt, wenn ihm etwas nicht gefällt.", EggLine.MEERI, Role.TEEN_B),
    POPCORNSCHWEINCHEN(Stage.ADULT, "Popcornschweinchen", "Hüpft vor Freude wie Popcorn in die Luft.", EggLine.MEERI, Role.ADULT_SPORTY),
    GURKENSCHWEINCHEN(Stage.ADULT, "Gurkenschweinchen", "Für eine Gurke tut es alles.", EggLine.MEERI, Role.ADULT_FOODIE),
    KUSCHELSCHWEINCHEN(Stage.ADULT, "Kuschelschweinchen", "Gurrt leise, wenn man es streichelt.", EggLine.MEERI, Role.ADULT_BALANCED),
    STRUPPELSCHWEINCHEN(Stage.ADULT, "Struppelschweinchen", "Etwas struppig, aber unglaublich lieb.", EggLine.MEERI, Role.SHADOW),
    SEIDENSCHWEINCHEN(Stage.ADULT, "Seidenschweinchen", "Mit langem, seidig glänzendem Fell. Legendär!", EggLine.MEERI, Role.LEGEND),

    // Ältere Formen: alle 10 Level eine neue Stufe, je Stufe zwei Wege (A aktiv, B gemütlich)
    // Knuffel, ab Level 20
    WIRBELKNUFFEL(Stage.PRAECHTIG, "Wirbelknuffel", "Ein flinker Knuffel, der ständig Purzelbäume schlägt.", EggLine.KNUFFEL, Role.S20_A),
    PLUESCHKNUFFEL(Stage.PRAECHTIG, "Plüschknuffel", "Noch flauschiger als früher, ein wandelndes Kissen.", EggLine.KNUFFEL, Role.S20_B),
    ABENTEUERKNUFFEL(Stage.ERFAHREN, "Abenteuerknuffel", "Trägt ein Halstuch und will die ganze Welt sehen.", EggLine.KNUFFEL, Role.S30_A),
    HERZKNUFFEL(Stage.ERFAHREN, "Herzknuffel", "Verteilt überall kleine Herzen.", EggLine.KNUFFEL, Role.S30_B),
    HIMMELSKNUFFEL(Stage.WEISE, "Himmelsknuffel", "Kleine Flügel tragen ihn über die Dächer.", EggLine.KNUFFEL, Role.S40_A),
    MONDKNUFFEL(Stage.WEISE, "Mondknuffel", "Träumt mit einem Mond auf dem Kopf.", EggLine.KNUFFEL, Role.S40_B),
    DRACHENKNUFFEL(Stage.EHRWUERDIG, "Drachenknuffel", "Ein Knuffel mit Drachenflügeln und Mut für zwei.", EggLine.KNUFFEL, Role.S50_A),
    KISSENKOENIG(Stage.EHRWUERDIG, "Kissenkönig", "Regiert gemütlich vom Sofa aus.", EggLine.KNUFFEL, Role.S50_B),
    URKNUFFEL(Stage.URALT, "Urknuffel", "Der Älteste seiner Art, mit silbernem Fell und weisen Augen.", EggLine.KNUFFEL, Role.S60_A),
    SILBERKNUFFEL(Stage.URALT, "Silberknuffel", "Sein Fell ist silbern geworden, sein Herz ist jung geblieben.", EggLine.KNUFFEL, Role.S60_B),
    STERNENKNUFFEL(Stage.ZEITLOS, "Sternenknuffel", "Halb Knuffel, halb Sternenlicht.", EggLine.KNUFFEL, Role.S70_A),
    ENGELSKNUFFEL(Stage.ZEITLOS, "Engelsknuffel", "Leuchtet sanft wie ein kleiner Engel.", EggLine.KNUFFEL, Role.S70_B),
    // Wald, ab Level 20
    RINDENSPRINGER(Stage.PRAECHTIG, "Rindenspringer", "Springt von Ast zu Ast wie ein Eichhörnchen.", EggLine.WALD, Role.S20_A),
    MOOSKISSEN(Stage.PRAECHTIG, "Mooskissen", "Weich wie ein Moospolster und riecht nach Wald.", EggLine.WALD, Role.S20_B),
    WALDLAEUFER(Stage.ERFAHREN, "Waldläufer", "Kennt jeden Pfad im ganzen Wald.", EggLine.WALD, Role.S30_A),
    BEERENBAER(Stage.ERFAHREN, "Beerenbär", "Sammelt Beeren für den langen Winter.", EggLine.WALD, Role.S30_B),
    EICHENHIRSCH(Stage.WEISE, "Eichenhirsch", "Ein Hirsch mit einem Geweih aus Eichenzweigen.", EggLine.WALD, Role.S40_A),
    BLUETENBAER(Stage.WEISE, "Blütenbär", "Auf seinem Rücken wächst eine kleine Blumenwiese.", EggLine.WALD, Role.S40_B),
    HAINWAECHTER(Stage.EHRWUERDIG, "Hainwächter", "Wacht über jeden Baum im Hain.", EggLine.WALD, Role.S50_A),
    PILZKOENIG(Stage.EHRWUERDIG, "Pilzkönig", "Trägt einen riesigen Pilz als Krone.", EggLine.WALD, Role.S50_B),
    MOOSGEWEIH(Stage.URALT, "Moosgeweih", "Sein Geweih ist mit Moos bewachsen, so alt ist er schon.", EggLine.WALD, Role.S60_A),
    KNORRBART(Stage.URALT, "Knorrbart", "Hat einen Bart aus Flechten und erzählt Waldgeschichten.", EggLine.WALD, Role.S60_B),
    WALDGEIST(Stage.ZEITLOS, "Waldgeist", "Ein leuchtender Geist, der durch den Wald schwebt.", EggLine.WALD, Role.S70_A),
    LEBENSBAUM(Stage.ZEITLOS, "Lebensbaum", "In ihm schlägt das Herz des ganzen Waldes.", EggLine.WALD, Role.S70_B),
    // Meer, ab Level 20
    WELLENREITER(Stage.PRAECHTIG, "Wellenreiter", "Reitet auf jeder noch so großen Welle.", EggLine.MEER, Role.S20_A),
    MUSCHELCHEN(Stage.PRAECHTIG, "Muschelchen", "Wohnt am liebsten in großen Muscheln.", EggLine.MEER, Role.S20_B),
    STROEMLING(Stage.ERFAHREN, "Strömling", "Schwimmt schneller als jede Strömung.", EggLine.MEER, Role.S30_A),
    PERLENWAL(Stage.ERFAHREN, "Perlenwal", "Ein runder Wal mit einer Perlenkette.", EggLine.MEER, Role.S30_B),
    TIEFSEEFLITZER(Stage.WEISE, "Tiefseeflitzer", "Erforscht die dunkle Tiefsee mit seiner Laterne.", EggLine.MEER, Role.S40_A),
    KORALLENHUETER(Stage.WEISE, "Korallenhüter", "Pflegt ein ganzes Korallenriff.", EggLine.MEER, Role.S40_B),
    MEERESDRACHE(Stage.EHRWUERDIG, "Meeresdrache", "Ein Drache aus Wasser und Gischt.", EggLine.MEER, Role.S50_A),
    GEZEITENWAL(Stage.EHRWUERDIG, "Gezeitenwal", "Wenn er singt, hört das ganze Meer zu.", EggLine.MEER, Role.S50_B),
    ALTERSEEBAER(Stage.URALT, "Alter Seebär", "Hat alle Meere der Welt gesehen.", EggLine.MEER, Role.S60_A),
    PERLMUTTGREIS(Stage.URALT, "Perlmuttgreis", "Schimmert wie das Innere einer Muschel.", EggLine.MEER, Role.S60_B),
    OZEANOS(Stage.ZEITLOS, "Ozeanos", "Der leuchtende Geist des Ozeans.", EggLine.MEER, Role.S70_A),
    MONDFLUT(Stage.ZEITLOS, "Mondflut", "Tanzt mit dem Mond über die Wellen.", EggLine.MEER, Role.S70_B),
    // Feuer, ab Level 20
    FUNKENFLITZER(Stage.PRAECHTIG, "Funkenflitzer", "Lässt Funken sprühen, wenn er rennt.", EggLine.FEUER, Role.S20_A),
    GLUTKISSEN(Stage.PRAECHTIG, "Glutkissen", "Warm wie ein Kachelofen im Winter.", EggLine.FEUER, Role.S20_B),
    FLAMMENLAEUFER(Stage.ERFAHREN, "Flammenläufer", "Läuft auf kleinen Flammenfüßen.", EggLine.FEUER, Role.S30_A),
    OFENBAER(Stage.ERFAHREN, "Ofenbär", "Backt Brötchen mit seinem warmen Bauch.", EggLine.FEUER, Role.S30_B),
    LAVADRACHE(Stage.WEISE, "Lavadrache", "Ein Drache mit Lava in den Adern.", EggLine.FEUER, Role.S40_A),
    KAMINWAECHTER(Stage.WEISE, "Kaminwächter", "Hält das Feuer im Kamin am Leben.", EggLine.FEUER, Role.S40_B),
    FEUERFUERST(Stage.EHRWUERDIG, "Feuerfürst", "Herrscht über alle Flammen.", EggLine.FEUER, Role.S50_A),
    SONNENOFEN(Stage.EHRWUERDIG, "Sonnenofen", "Wärmt jeden, der friert.", EggLine.FEUER, Role.S50_B),
    ASCHEDRACHE(Stage.URALT, "Aschedrache", "Ein alter Drache mit grauen Schuppen und glühenden Augen.", EggLine.FEUER, Role.S60_A),
    GLIMMGREIS(Stage.URALT, "Glimmgreis", "Seine Glut glimmt ruhig und warm.", EggLine.FEUER, Role.S60_B),
    SONNENDRACHE(Stage.ZEITLOS, "Sonnendrache", "Leuchtet wie die Sonne selbst.", EggLine.FEUER, Role.S70_A),
    EWIGEFLAMME(Stage.ZEITLOS, "Ewige Flamme", "Eine Flamme, die niemals erlischt.", EggLine.FEUER, Role.S70_B),
    // Urzeit, ab Level 20
    SPRINTRAPTOR(Stage.PRAECHTIG, "Sprintraptor", "Schneller als jeder andere Dino.", EggLine.URZEIT, Role.S20_A),
    KUSCHELSAURUS(Stage.PRAECHTIG, "Kuschelsaurus", "Der gemütlichste Dino der Urzeit.", EggLine.URZEIT, Role.S20_B),
    HORNSAURUS(Stage.ERFAHREN, "Hornsaurus", "Stolz auf seine drei großen Hörner.", EggLine.URZEIT, Role.S30_A),
    BLATTFRESSER(Stage.ERFAHREN, "Blattfresser", "Frisst am liebsten frische Farnblätter.", EggLine.URZEIT, Role.S30_B),
    FLUGSAURIER(Stage.WEISE, "Flugsaurier", "Segelt hoch über rauchende Vulkane.", EggLine.URZEIT, Role.S40_A),
    PANZERSAURUS(Stage.WEISE, "Panzersaurus", "Hat einen dicken Panzer auf dem Rücken.", EggLine.URZEIT, Role.S40_B),
    DONNERECHSE(Stage.EHRWUERDIG, "Donnerechse", "Wenn sie läuft, bebt die Erde.", EggLine.URZEIT, Role.S50_A),
    RIESENSAURUS(Stage.EHRWUERDIG, "Riesensaurus", "So groß wie ein Haus und ganz sanft.", EggLine.URZEIT, Role.S50_B),
    FOSSILKOENIG(Stage.URALT, "Fossilkönig", "So alt wie die Steine selbst.", EggLine.URZEIT, Role.S60_A),
    STEINSAURUS(Stage.URALT, "Steinsaurus", "Halb versteinert, aber hellwach.", EggLine.URZEIT, Role.S60_B),
    METEORSAURUS(Stage.ZEITLOS, "Meteorsaurus", "Kam mit einem Meteor und leuchtet seitdem.", EggLine.URZEIT, Role.S70_A),
    BERNSTEINSAURUS(Stage.ZEITLOS, "Bernsteinsaurus", "Funkelt golden wie Bernstein.", EggLine.URZEIT, Role.S70_B),
    // Techno, ab Level 20
    TURBOBOT(Stage.PRAECHTIG, "Turbobot", "Mit eingebautem Turbo-Antrieb.", EggLine.TECHNO, Role.S20_A),
    KUSCHELBOT(Stage.PRAECHTIG, "Kuschelbot", "Ein weicher Roboter zum Knuddeln.", EggLine.TECHNO, Role.S20_B),
    DUESENBOT(Stage.ERFAHREN, "Düsenbot", "Fliegt mit zwei kleinen Düsen.", EggLine.TECHNO, Role.S30_A),
    TEEBOT(Stage.ERFAHREN, "Teebot", "Kocht den besten Tee der Welt.", EggLine.TECHNO, Role.S30_B),
    MECHADRACHE(Stage.WEISE, "Mechadrache", "Ein Drache aus Zahnrädern und Schrauben.", EggLine.TECHNO, Role.S40_A),
    GARTENBOT(Stage.WEISE, "Gartenbot", "Pflegt Blumen mit größter Präzision.", EggLine.TECHNO, Role.S40_B),
    TITANBOT(Stage.EHRWUERDIG, "Titanbot", "Stark wie ein Baukran.", EggLine.TECHNO, Role.S50_A),
    HAUSMEISTERBOT(Stage.EHRWUERDIG, "Hausmeisterbot", "Repariert alles im ganzen Haus.", EggLine.TECHNO, Role.S50_B),
    RETROBOT(Stage.URALT, "Retrobot", "Ein Roboter aus alten Zeiten, mit Röhren und Knöpfen.", EggLine.TECHNO, Role.S60_A),
    ROSTGREIS(Stage.URALT, "Rostgreis", "Etwas rostig, aber voller Erinnerungen.", EggLine.TECHNO, Role.S60_B),
    QUANTENBOT(Stage.ZEITLOS, "Quantenbot", "Ist an zwei Orten gleichzeitig.", EggLine.TECHNO, Role.S70_A),
    STERNENCOMPUTER(Stage.ZEITLOS, "Sternencomputer", "Berechnet die Bahnen aller Sterne.", EggLine.TECHNO, Role.S70_B),
    // Einhorn, ab Level 20
    GALOPPHORN(Stage.PRAECHTIG, "Galopphorn", "Galoppiert über Wiesen und Wolken.", EggLine.EINHORN, Role.S20_A),
    WATTEHORN(Stage.PRAECHTIG, "Wattehorn", "Weich wie Zuckerwatte.", EggLine.EINHORN, Role.S20_B),
    WOLKENRENNER(Stage.ERFAHREN, "Wolkenrenner", "Rennt über die Wolken.", EggLine.EINHORN, Role.S30_A),
    BLUETENHORN(Stage.ERFAHREN, "Blütenhorn", "Trägt einen Blumenkranz um den Hals.", EggLine.EINHORN, Role.S30_B),
    HIMMELSROSS(Stage.WEISE, "Himmelsross", "Ein geflügeltes Pferd des Himmels.", EggLine.EINHORN, Role.S40_A),
    TRAUMHORN(Stage.WEISE, "Traumhorn", "Bringt allen schöne Träume.", EggLine.EINHORN, Role.S40_B),
    REGENBOGENRITTER(Stage.EHRWUERDIG, "Regenbogenritter", "Kämpft für alles Gute auf der Welt.", EggLine.EINHORN, Role.S50_A),
    HERZHORN(Stage.EHRWUERDIG, "Herzhorn", "Sein Horn leuchtet in Herzform.", EggLine.EINHORN, Role.S50_B),
    SILBERHORN(Stage.URALT, "Silberhorn", "Mähne und Horn sind silbern geworden.", EggLine.EINHORN, Role.S60_A),
    MONDMAEHNE(Stage.URALT, "Mondmähne", "Seine Mähne leuchtet wie Mondlicht.", EggLine.EINHORN, Role.S60_B),
    STERNENROSS(Stage.ZEITLOS, "Sternenross", "Galoppiert über die Milchstraße.", EggLine.EINHORN, Role.S70_A),
    LICHTHORN(Stage.ZEITLOS, "Lichthorn", "Ein Einhorn aus reinem Licht.", EggLine.EINHORN, Role.S70_B),
    // Bluete, ab Level 20
    BLUETENFLITZ(Stage.PRAECHTIG, "Blütenflitz", "Flitzt von Blüte zu Blüte.", EggLine.BLUETE, Role.S20_A),
    POLLENPUSCHEL(Stage.PRAECHTIG, "Pollenpuschel", "Ein flauschiger Ball aus Blütenstaub.", EggLine.BLUETE, Role.S20_B),
    WINDBLUETE(Stage.ERFAHREN, "Windblüte", "Lässt sich vom Wind tragen.", EggLine.BLUETE, Role.S30_A),
    HONIGBLUETE(Stage.ERFAHREN, "Honigblüte", "Duftet herrlich nach Honig.", EggLine.BLUETE, Role.S30_B),
    SCHMETTERLINGSFEE(Stage.WEISE, "Schmetterlingsfee", "Hat große, bunte Schmetterlingsflügel.", EggLine.BLUETE, Role.S40_A),
    ROSENHERZ(Stage.WEISE, "Rosenherz", "Hat ein Herz aus Rosenblättern.", EggLine.BLUETE, Role.S40_B),
    LILIENFEE(Stage.EHRWUERDIG, "Lilienfee", "Elegant wie eine weiße Lilie.", EggLine.BLUETE, Role.S50_A),
    GARTENHUETERIN(Stage.EHRWUERDIG, "Gartenhüterin", "Lässt den ganzen Garten blühen.", EggLine.BLUETE, Role.S50_B),
    HERBSTFEE(Stage.URALT, "Herbstfee", "Ihre Flügel leuchten in Herbstfarben.", EggLine.BLUETE, Role.S60_A),
    LAVENDELHAUBE(Stage.URALT, "Lavendelhaube", "Duftet nach Lavendel und kennt jede Gartenregel.", EggLine.BLUETE, Role.S60_B),
    BLUETENGEIST(Stage.ZEITLOS, "Blütengeist", "Leuchtet wie ein Glühwürmchen in der Nacht.", EggLine.BLUETE, Role.S70_A),
    EWIGEROSE(Stage.ZEITLOS, "Ewige Rose", "Eine Rose, die niemals verblüht.", EggLine.BLUETE, Role.S70_B),
    // Stern, ab Level 20
    STERNSCHNUPPE(Stage.PRAECHTIG, "Sternschnuppe", "Saust funkelnd über den Himmel.", EggLine.STERN, Role.S20_A),
    KUSCHELSTERN(Stage.PRAECHTIG, "Kuschelstern", "Ein weicher, leuchtender Stern.", EggLine.STERN, Role.S20_B),
    KOMETENREITER(Stage.ERFAHREN, "Kometenreiter", "Reitet auf vorbeifliegenden Kometen.", EggLine.STERN, Role.S30_A),
    MONDKISSEN(Stage.ERFAHREN, "Mondkissen", "Schläft am liebsten auf dem Mond.", EggLine.STERN, Role.S30_B),
    STERNENPILOT(Stage.WEISE, "Sternenpilot", "Fliegt mit seinem Raumschiff zu fernen Welten.", EggLine.STERN, Role.S40_A),
    PLANETENHUETER(Stage.WEISE, "Planetenhüter", "Hat einen eigenen kleinen Planeten.", EggLine.STERN, Role.S40_B),
    POLARSTERN(Stage.EHRWUERDIG, "Polarstern", "Zeigt jedem den richtigen Weg.", EggLine.STERN, Role.S50_A),
    NEBELSTERN(Stage.EHRWUERDIG, "Nebelstern", "Ein warmer, bunter Sternennebel.", EggLine.STERN, Role.S50_B),
    ALTERKOMET(Stage.URALT, "Alter Komet", "Kommt nur alle hundert Jahre vorbei.", EggLine.STERN, Role.S60_A),
    MONDGREIS(Stage.URALT, "Mondgreis", "Erzählt Geschichten vom Anfang des Universums.", EggLine.STERN, Role.S60_B),
    SUPERNOVA(Stage.ZEITLOS, "Supernova", "Strahlt heller als tausend Sonnen.", EggLine.STERN, Role.S70_A),
    GALAXIENHERZ(Stage.ZEITLOS, "Galaxienherz", "In seinem Herzen dreht sich eine Galaxie.", EggLine.STERN, Role.S70_B),
    // Zucker, ab Level 20
    BRAUSEBONBON(Stage.PRAECHTIG, "Brausebonbon", "Prickelt und hüpft bei jedem Schritt.", EggLine.ZUCKER, Role.S20_A),
    MARSHMALLOW(Stage.PRAECHTIG, "Marshmallow", "Weich, rund und herrlich süß.", EggLine.ZUCKER, Role.S20_B),
    ZUCKERSTANGE(Stage.ERFAHREN, "Zuckerstange", "Gestreift, flink und knackig.", EggLine.ZUCKER, Role.S30_A),
    PUDDING(Stage.ERFAHREN, "Pudding", "Wackelt bei jedem Schritt.", EggLine.ZUCKER, Role.S30_B),
    RAKETENEIS(Stage.WEISE, "Raketeneis", "Ein Eis am Stiel, das abheben kann.", EggLine.ZUCKER, Role.S40_A),
    SAHNETORTE(Stage.WEISE, "Sahnetorte", "Mit drei Etagen Sahne.", EggLine.ZUCKER, Role.S40_B),
    BONBONRITTER(Stage.EHRWUERDIG, "Bonbonritter", "Trägt eine Rüstung aus Hartkaramell.", EggLine.ZUCKER, Role.S50_A),
    PRALINENKOENIG(Stage.EHRWUERDIG, "Pralinenkönig", "Trägt eine Krone aus Schokolade.", EggLine.ZUCKER, Role.S50_B),
    ALTERLEBKUCHEN(Stage.URALT, "Alter Lebkuchen", "Knusprig, würzig und weise.", EggLine.ZUCKER, Role.S60_A),
    HONIGKUCHEN(Stage.URALT, "Honigkuchen", "Süß und vor lauter Alter ein bisschen klebrig.", EggLine.ZUCKER, Role.S60_B),
    ZUCKERSTERN(Stage.ZEITLOS, "Zuckerstern", "Ein glitzernder Stern aus Kandiszucker.", EggLine.ZUCKER, Role.S70_A),
    ZAUBERKONFEKT(Stage.ZEITLOS, "Zauberkonfekt", "Erfüllt jedem einen süßen Wunsch.", EggLine.ZUCKER, Role.S70_B),
    // Dschungel, ab Level 20
    LIANENSCHWINGER(Stage.PRAECHTIG, "Lianenschwinger", "Schwingt sich von Liane zu Liane.", EggLine.DSCHUNGEL, Role.S20_A),
    MANGOMAMPF(Stage.PRAECHTIG, "Mangomampf", "Isst den ganzen Tag Mangos.", EggLine.DSCHUNGEL, Role.S20_B),
    PANTHERPFOTE(Stage.ERFAHREN, "Pantherpfote", "Schleicht lautlos durchs Dickicht.", EggLine.DSCHUNGEL, Role.S30_A),
    FAULTI(Stage.ERFAHREN, "Faulti", "Ein gemütliches Faultier, das nie hetzt.", EggLine.DSCHUNGEL, Role.S30_B),
    PARADIESVOGEL(Stage.WEISE, "Paradiesvogel", "Hat prächtige, bunte Federn.", EggLine.DSCHUNGEL, Role.S40_A),
    ORCHIDEENAFFE(Stage.WEISE, "Orchideenaffe", "Trägt Orchideen in seinem Fell.", EggLine.DSCHUNGEL, Role.S40_B),
    REGENWALDWAECHTER(Stage.EHRWUERDIG, "Regenwaldwächter", "Beschützt den Regenwald vor allem Bösen.", EggLine.DSCHUNGEL, Role.S50_A),
    BANANENKOENIG(Stage.EHRWUERDIG, "Bananenkönig", "Herrscht über alle Bananenstauden.", EggLine.DSCHUNGEL, Role.S50_B),
    ALTERJAGUAR(Stage.URALT, "Alter Jaguar", "Seine Flecken sind grau, seine Augen so scharf wie immer.", EggLine.DSCHUNGEL, Role.S60_A),
    SILBERRUECKEN(Stage.URALT, "Silberrücken", "Ein weiser alter Gorilla mit silbernem Rücken.", EggLine.DSCHUNGEL, Role.S60_B),
    DSCHUNGELGEIST(Stage.ZEITLOS, "Dschungelgeist", "Der leuchtende Geist des Regenwalds.", EggLine.DSCHUNGEL, Role.S70_A),
    SMARAGDHERZ(Stage.ZEITLOS, "Smaragdherz", "In seiner Brust schlägt ein Herz aus Smaragd.", EggLine.DSCHUNGEL, Role.S70_B),
    // Wueste, ab Level 20
    DUENENFLITZER(Stage.PRAECHTIG, "Dünenflitzer", "Flitzt über die heißen Dünen.", EggLine.WUESTE, Role.S20_A),
    DATTELCHEN(Stage.PRAECHTIG, "Dattelchen", "Liebt süße Datteln über alles.", EggLine.WUESTE, Role.S20_B),
    WUESTENWIND(Stage.ERFAHREN, "Wüstenwind", "Weht wie ein warmer Wind über den Sand.", EggLine.WUESTE, Role.S30_A),
    OASENWAECHTER(Stage.ERFAHREN, "Oasenwächter", "Findet immer frisches Wasser.", EggLine.WUESTE, Role.S30_B),
    SANDLAEUFER(Stage.WEISE, "Sandläufer", "Hinterlässt im Sand nie eine Spur.", EggLine.WUESTE, Role.S40_A),
    KARAWANENFREUND(Stage.WEISE, "Karawanenfreund", "Begleitet Karawanen durch die Wüste.", EggLine.WUESTE, Role.S40_B),
    SONNENSKARABAEUS(Stage.EHRWUERDIG, "Sonnenskarabäus", "Rollt die Sonne über den Himmel.", EggLine.WUESTE, Role.S50_A),
    RAETSELSPHINX(Stage.EHRWUERDIG, "Rätselsphinx", "Stellt jedem drei Rätsel.", EggLine.WUESTE, Role.S50_B),
    ALTERWUESTENFUCHS(Stage.URALT, "Alter Wüstenfuchs", "Kennt jedes Sandkorn der Wüste.", EggLine.WUESTE, Role.S60_A),
    SANDGREIS(Stage.URALT, "Sandgreis", "So alt wie die Pyramiden.", EggLine.WUESTE, Role.S60_B),
    GOLDENEDUENE(Stage.ZEITLOS, "Goldene Düne", "Funkelt wie Gold in der Abendsonne.", EggLine.WUESTE, Role.S70_A),
    STERNENSPHINX(Stage.ZEITLOS, "Sternensphinx", "Bewacht die Sterne über der Wüste.", EggLine.WUESTE, Role.S70_B),
    // Edelstein, ab Level 20
    TOPASFLITZ(Stage.PRAECHTIG, "Topasflitz", "Ein flinker, gelb funkelnder Topas.", EggLine.EDELSTEIN, Role.S20_A),
    ROSENQUARZ(Stage.PRAECHTIG, "Rosenquarz", "Rosa und kuschelig warm.", EggLine.EDELSTEIN, Role.S20_B),
    SMARAGDLAEUFER(Stage.ERFAHREN, "Smaragdläufer", "Grün funkelnd und schnell.", EggLine.EDELSTEIN, Role.S30_A),
    ACHATBAER(Stage.ERFAHREN, "Achatbär", "Rund und voller gestreifter Muster.", EggLine.EDELSTEIN, Role.S30_B),
    SAPHIRDRACHE(Stage.WEISE, "Saphirdrache", "Ein Drache aus blauem Saphir.", EggLine.EDELSTEIN, Role.S40_A),
    PERLMUTTHERZ(Stage.WEISE, "Perlmuttherz", "Schimmert in sanften Farben.", EggLine.EDELSTEIN, Role.S40_B),
    KRISTALLRITTER(Stage.EHRWUERDIG, "Kristallritter", "Trägt eine Rüstung aus Bergkristall.", EggLine.EDELSTEIN, Role.S50_A),
    KRONJUWEL(Stage.EHRWUERDIG, "Kronjuwel", "Funkelt wie der schönste Edelstein der Krone.", EggLine.EDELSTEIN, Role.S50_B),
    ALTEROBSIDIAN(Stage.URALT, "Alter Obsidian", "Schwarz, glatt und uralt.", EggLine.EDELSTEIN, Role.S60_A),
    MONDSTEIN(Stage.URALT, "Mondstein", "Schimmert silbern wie der Mond.", EggLine.EDELSTEIN, Role.S60_B),
    STERNENDIAMANT(Stage.ZEITLOS, "Sternendiamant", "Funkelt wie ein Stern am Himmel.", EggLine.EDELSTEIN, Role.S70_A),
    REGENBOGENKRISTALL(Stage.ZEITLOS, "Regenbogenkristall", "Bricht das Licht in alle Farben.", EggLine.EDELSTEIN, Role.S70_B),
    // Frost, ab Level 20
    SCHNEEFLITZER(Stage.PRAECHTIG, "Schneeflitzer", "Rutscht auf dem Bauch übers Eis.", EggLine.FROST, Role.S20_A),
    SCHNEEBALL(Stage.PRAECHTIG, "Schneeball", "Kugelrund, weiß und kalt.", EggLine.FROST, Role.S20_B),
    EISLAEUFER(Stage.ERFAHREN, "Eisläufer", "Dreht Pirouetten auf dem zugefrorenen See.", EggLine.FROST, Role.S30_A),
    PUDELMUETZE(Stage.ERFAHREN, "Pudelmütze", "Trägt immer eine warme Mütze.", EggLine.FROST, Role.S30_B),
    FROSTFALKE(Stage.WEISE, "Frostfalke", "Fliegt mitten durch Schneestürme.", EggLine.FROST, Role.S40_A),
    IGLUBAER(Stage.WEISE, "Iglubär", "Baut die schönsten Iglus.", EggLine.FROST, Role.S40_B),
    NORDLICHTWOLF(Stage.EHRWUERDIG, "Nordlichtwolf", "Heult zusammen mit dem Nordlicht.", EggLine.FROST, Role.S50_A),
    SCHNEEKOENIG(Stage.EHRWUERDIG, "Schneekönig", "Herrscht gemütlich über den Winter.", EggLine.FROST, Role.S50_B),
    GLETSCHERWOLF(Stage.URALT, "Gletscherwolf", "So alt wie das ewige Eis.", EggLine.FROST, Role.S60_A),
    FROSTBART(Stage.URALT, "Frostbart", "Hat einen langen Bart aus Eiszapfen.", EggLine.FROST, Role.S60_B),
    POLARLICHT(Stage.ZEITLOS, "Polarlicht", "Tanzt als Licht über den Nachthimmel.", EggLine.FROST, Role.S70_A),
    EWIGESEIS(Stage.ZEITLOS, "Ewiges Eis", "Ein Kristall, der niemals schmilzt.", EggLine.FROST, Role.S70_B),
    // Grusel, ab Level 20
    SPUKFLITZER(Stage.PRAECHTIG, "Spukflitzer", "Huscht durch dunkle Flure.", EggLine.GRUSEL, Role.S20_A),
    KUERBISKUCHEN(Stage.PRAECHTIG, "Kürbiskuchen", "Riecht nach Zimt und Kürbis.", EggLine.GRUSEL, Role.S20_B),
    NACHTSCHWAERMER(Stage.ERFAHREN, "Nachtschwärmer", "Ist nur nachts unterwegs.", EggLine.GRUSEL, Role.S30_A),
    HEXENKESSEL(Stage.ERFAHREN, "Hexenkessel", "Braut lustige, bunte Tränke.", EggLine.GRUSEL, Role.S30_B),
    FLEDERMAUSGRAF(Stage.WEISE, "Fledermausgraf", "Ein vornehmer Graf mit Fledermausflügeln.", EggLine.GRUSEL, Role.S40_A),
    ZAUBERKUERBIS(Stage.WEISE, "Zauberkürbis", "Ein Kürbis voller Magie.", EggLine.GRUSEL, Role.S40_B),
    SCHATTENRITTER(Stage.EHRWUERDIG, "Schattenritter", "Reitet durch die Halloween-Nacht.", EggLine.GRUSEL, Role.S50_A),
    SCHLOSSGESPENST(Stage.EHRWUERDIG, "Schlossgespenst", "Spukt in einem alten Schloss, ganz freundlich.", EggLine.GRUSEL, Role.S50_B),
    URALTERVAMPIR(Stage.URALT, "Uralter Vampir", "Schon 500 Jahre alt und immer noch höflich.", EggLine.GRUSEL, Role.S60_A),
    SPINNWEBGREIS(Stage.URALT, "Spinnwebgreis", "Ganz von weichen Spinnweben umhüllt.", EggLine.GRUSEL, Role.S60_B),
    GEISTERLICHT(Stage.ZEITLOS, "Geisterlicht", "Ein Irrlicht, das in der Nacht tanzt.", EggLine.GRUSEL, Role.S70_A),
    MONDKUERBIS(Stage.ZEITLOS, "Mondkürbis", "Leuchtet wie der Vollmond.", EggLine.GRUSEL, Role.S70_B),
    // Katze, ab Level 20
    STREIFZUEGLERIN(Stage.PRAECHTIG, "Streifzüglerin", "Erkundet jeden Garten in der Nachbarschaft.", EggLine.KATZE, Role.S20_A),
    SOFAKATZE(Stage.PRAECHTIG, "Sofakatze", "Kennt jeden gemütlichen Platz im Haus.", EggLine.KATZE, Role.S20_B),
    KLETTERKATZE(Stage.ERFAHREN, "Kletterkatze", "Klettert auf jeden Baum und jedes Regal.", EggLine.KATZE, Role.S30_A),
    SCHNURRMEISTERIN(Stage.ERFAHREN, "Schnurrmeisterin", "Schnurrt lauter als ein Motor.", EggLine.KATZE, Role.S30_B),
    GARTENWAECHTERIN(Stage.WEISE, "Gartenwächterin", "Bewacht den Garten vor jeder Maus.", EggLine.KATZE, Role.S40_A),
    FENSTERBANKKATZE(Stage.WEISE, "Fensterbankkatze", "Beobachtet den ganzen Tag die Vögel.", EggLine.KATZE, Role.S40_B),
    ERFAHRENEJAEGERIN(Stage.EHRWUERDIG, "Erfahrene Jägerin", "Hat in ihrem Leben schon alles gesehen.", EggLine.KATZE, Role.S50_A),
    HAUSKOENIGIN(Stage.EHRWUERDIG, "Hauskönigin", "Das ganze Haus gehört ihr.", EggLine.KATZE, Role.S50_B),
    GRAUEPFOTE(Stage.URALT, "Graue Pfote", "Ihre Schnauze ist grau geworden, ihr Blick ganz sanft.", EggLine.KATZE, Role.S60_A),
    SONNENPLATZKATZE(Stage.URALT, "Sonnenplatzkatze", "Döst am liebsten im warmen Sonnenfleck.", EggLine.KATZE, Role.S60_B),
    WEISEKATZE(Stage.ZEITLOS, "Weise Katze", "Uralt, ruhig und voller Weisheit.", EggLine.KATZE, Role.S70_A),
    HERZENSKATZE(Stage.ZEITLOS, "Herzenskatze", "Ein ganzes Leben voller Liebe.", EggLine.KATZE, Role.S70_B),
    // Hase, ab Level 20
    WIESENHASE(Stage.PRAECHTIG, "Wiesenhase", "Rast über die Frühlingswiese.", EggLine.HASE, Role.S20_A),
    MOEHRENFREUND(Stage.PRAECHTIG, "Möhrenfreund", "Hat immer eine Möhre dabei.", EggLine.HASE, Role.S20_B),
    HAKENSCHLAEGER(Stage.ERFAHREN, "Hakenschläger", "Schlägt Haken wie ein echter Feldhase.", EggLine.HASE, Role.S30_A),
    HEUHASE(Stage.ERFAHREN, "Heuhase", "Baut sich ein weiches Nest aus Heu.", EggLine.HASE, Role.S30_B),
    LANGOHR(Stage.WEISE, "Langohr", "Mit besonders langen Ohren.", EggLine.HASE, Role.S40_A),
    WIDDERHASE(Stage.WEISE, "Widderhase", "Mit flauschigen Schlappohren.", EggLine.HASE, Role.S40_B),
    FELDHUETER(Stage.EHRWUERDIG, "Feldhüter", "Kennt jedes Kaninchenloch.", EggLine.HASE, Role.S50_A),
    SAMTHASE(Stage.EHRWUERDIG, "Samthase", "Sein Fell ist weich wie Samt.", EggLine.HASE, Role.S50_B),
    GRAUESLANGOHR(Stage.URALT, "Graues Langohr", "Das Fell um die Nase ist grau geworden.", EggLine.HASE, Role.S60_A),
    SONNENHASE(Stage.URALT, "Sonnenhase", "Liegt am liebsten in der Sonne.", EggLine.HASE, Role.S60_B),
    WEISERHASE(Stage.ZEITLOS, "Weiser Hase", "Ein uralter, weiser Hase.", EggLine.HASE, Role.S70_A),
    HERZENSHASE(Stage.ZEITLOS, "Herzenshase", "Ein Leben voller Möhren und Liebe.", EggLine.HASE, Role.S70_B),
    // Hamster, ab Level 20
    SPRINTHAMSTER(Stage.PRAECHTIG, "Sprinthamster", "Schafft im Laufrad die meisten Runden.", EggLine.HAMSTER, Role.S20_A),
    PAUSBAECKCHEN(Stage.PRAECHTIG, "Pausbäckchen", "Mit den rundesten Backen der Welt.", EggLine.HAMSTER, Role.S20_B),
    TUNNELGRAEBER(Stage.ERFAHREN, "Tunnelgräber", "Gräbt die längsten Tunnel.", EggLine.HAMSTER, Role.S30_A),
    VORRATSMEISTER(Stage.ERFAHREN, "Vorratsmeister", "Hat Vorräte für ein ganzes Jahr.", EggLine.HAMSTER, Role.S30_B),
    KLETTERHAMSTER(Stage.WEISE, "Kletterhamster", "Klettert durch jedes Gitter.", EggLine.HAMSTER, Role.S40_A),
    GOLDBAECKCHEN(Stage.WEISE, "Goldbäckchen", "Mit goldenem Fell und vollen Backen.", EggLine.HAMSTER, Role.S40_B),
    LABYRINTHPROFI(Stage.EHRWUERDIG, "Labyrinthprofi", "Findet aus jedem Labyrinth heraus.", EggLine.HAMSTER, Role.S50_A),
    KUSCHELKUGEL(Stage.EHRWUERDIG, "Kuschelkugel", "Rollt sich zur perfekten Kugel.", EggLine.HAMSTER, Role.S50_B),
    GRAUERFLITZER(Stage.URALT, "Grauer Flitzer", "Etwas langsamer, aber immer noch neugierig.", EggLine.HAMSTER, Role.S60_A),
    SCHLAFMUETZE(Stage.URALT, "Schlafmütze", "Schläft fast den ganzen Tag.", EggLine.HAMSTER, Role.S60_B),
    WEISERHAMSTER(Stage.ZEITLOS, "Weiser Hamster", "Ein uralter Hamster mit weisen Knopfaugen.", EggLine.HAMSTER, Role.S70_A),
    HERZENSHAMSTER(Stage.ZEITLOS, "Herzenshamster", "Ein kleines Leben voller großer Liebe.", EggLine.HAMSTER, Role.S70_B),
    // Meeri, ab Level 20
    HUEPFSCHWEINCHEN(Stage.PRAECHTIG, "Hüpfschweinchen", "Macht vor Freude Luftsprünge.", EggLine.MEERI, Role.S20_A),
    LOEWENZAHNFREUND(Stage.PRAECHTIG, "Löwenzahnfreund", "Liebt frischen Löwenzahn.", EggLine.MEERI, Role.S20_B),
    RENNSCHWEINCHEN(Stage.ERFAHREN, "Rennschweinchen", "Saust quiekend durchs Gehege.", EggLine.MEERI, Role.S30_A),
    HEUSCHWEINCHEN(Stage.ERFAHREN, "Heuschweinchen", "Wohnt am liebsten mitten im Heu.", EggLine.MEERI, Role.S30_B),
    WIRBELWIND(Stage.WEISE, "Wirbelwind", "Hat überall lustige Fellwirbel.", EggLine.MEERI, Role.S40_A),
    LANGHAARSCHWEINCHEN(Stage.WEISE, "Langhaarschweinchen", "Mit langem, weichem Fell.", EggLine.MEERI, Role.S40_B),
    QUIEKMEISTER(Stage.EHRWUERDIG, "Quiekmeister", "Quiekt am lautesten, wenn es Futter gibt.", EggLine.MEERI, Role.S50_A),
    PLUESCHSCHWEINCHEN(Stage.EHRWUERDIG, "Plüschschweinchen", "Weich wie ein Plüschtier.", EggLine.MEERI, Role.S50_B),
    GRAUNAESCHEN(Stage.URALT, "Graunäschen", "Sein Näschen ist grau geworden.", EggLine.MEERI, Role.S60_A),
    GEMUETLICHSCHWEINCHEN(Stage.URALT, "Gemütlichschweinchen", "Macht nur noch, was ihm gefällt.", EggLine.MEERI, Role.S60_B),
    WEISESMEERSCHWEINCHEN(Stage.ZEITLOS, "Weises Meerschweinchen", "Uralt und unglaublich gelassen.", EggLine.MEERI, Role.S70_A),
    HERZENSSCHWEINCHEN(Stage.ZEITLOS, "Herzensschweinchen", "Ein Leben voller Gurken und Liebe.", EggLine.MEERI, Role.S70_B),
    ;

    val isLegend: Boolean get() = role == Role.LEGEND
    val isCat: Boolean get() = line == EggLine.KATZE

    /** Real animals (cats and small animals). */
    val isReal: Boolean get() = line.real

    companion object {
        /** All fantasy creatures that can be collected (without the egg and the real animals). */
        val creatures: List<Form> get() = entries.filter { it != EGG && it.line.fantasy }

        /** The real cats of the cat mode. */
        val cats: List<Form> get() = entries.filter { it.isCat }

        /** Real small animals: rabbits, hamsters and guinea pigs. */
        val smallAnimals: List<Form> get() = entries.filter { it.line.small }

        fun find(line: EggLine, role: Role): Form? = entries.firstOrNull { it.line == line && it.role == role }
    }
}
