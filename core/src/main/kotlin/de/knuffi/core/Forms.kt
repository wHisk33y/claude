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
}

/** Egg types. Every line has its own family tree of creatures. */
@Serializable
enum class EggLine(
    val title: String,
    val emoji: String,
    val description: String,
    val look: LookStyle? = null,
    val eventOnly: Boolean = false,
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
    FROST("Frost-Ei", "❄️", "Eiskalt! Gibt es nur zum Winterzauber.", eventOnly = true),
    GRUSEL("Grusel-Ei", "🎃", "Huuuh! Gibt es nur zu Halloween.", eventOnly = true),
    ;

    /** All creatures (without the egg) that can hatch from this line. */
    val forms: List<Form> get() = Form.entries.filter { it.line == this && it != Form.EGG }
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
    ;

    val isLegend: Boolean get() = role == Role.LEGEND

    companion object {
        /** All creatures that can be collected (without the egg). */
        val creatures: List<Form> get() = entries.filter { it != EGG }

        fun find(line: EggLine, role: Role): Form? = entries.firstOrNull { it.line == line && it.role == role }
    }
}
