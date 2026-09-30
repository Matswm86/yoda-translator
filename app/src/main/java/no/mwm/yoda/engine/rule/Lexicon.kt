package no.mwm.yoda.engine.rule

import no.mwm.yoda.engine.Lang

/**
 * Closed-class word lists plus a core open-class verb list, per language.
 *
 * Closed classes (pronouns, modals, auxiliaries, subordinators, prepositions)
 * are small, finite and stable, so listing them is exact rather than a guess.
 * Open-class verbs are covered by a common-verb list first and suffix rules
 * second; the suffix rules are guarded so that a noun sitting after a
 * determiner is never mistaken for a finite verb.
 */
object Lexicon {

    // ---------------------------------------------------------------- Norwegian

    private val NO_PRON = setOf(
        "jeg", "eg", "meg", "du", "deg", "han", "ham", "hun", "henne", "hen",
        "den", "det", "vi", "oss", "dere", "de", "dem", "seg", "man",
        "min", "mitt", "mine", "din", "ditt", "dine", "hans", "hennes",
        "vår", "vårt", "våre", "deres", "sin", "sitt", "sine",
        "hverandre", "noen", "ingen", "alle", "alt"
    )

    private val NO_MODAL = setOf(
        "vil", "ville", "kan", "kunne", "skal", "skulle",
        "må", "måtte", "bør", "burde", "tør", "torde", "får", "fikk"
    )

    private val NO_AUX = setOf(
        "har", "hadde", "er", "var", "blir", "ble", "være", "vært", "blitt",
        "havde", "haver"
    )

    private val NO_SUBORD = setOf(
        "hvis", "dersom", "når", "fordi", "at", "som", "da", "mens", "om",
        "ettersom", "før", "etter", "siden", "enn", "hvorfor", "hvordan",
        "hvem", "hva", "hvilken", "hvilket", "hvilke", "hvor", "sånn", "slik"
    )

    private val NO_COORD = setOf("og", "men", "eller", "samt", "for", "så")

    private val NO_NEG = setOf("ikke", "aldri", "ikkje")

    private val NO_PREP = setOf(
        "i", "på", "til", "fra", "med", "av", "ved", "under", "over",
        "mellom", "gjennom", "mot", "uten", "hos", "bak", "foran", "rundt",
        "langs", "omkring", "blant", "inntil", "ifølge", "utenom", "innen",
        "per", "pr", "via", "trass"
    )

    private val NO_DET = setOf(
        "en", "ei", "et", "den", "det", "de", "denne", "dette", "disse",
        "hver", "hvert", "hvor", "mange", "få", "all", "alle", "alt",
        "begge", "ethvert", "enhver", "noe", "annen", "annet", "andre",
        "samme", "selve", "flere", "mye"
    )

    private val NO_ADV = setOf(
        "alltid", "ofte", "sjelden", "veldig", "ganske", "litt", "mye",
        "hardt", "godt", "bra", "dårlig", "nå", "her", "der", "hjemme",
        "ute", "inne", "oppe", "nede", "snart", "straks", "igjen", "også",
        "bare", "kun", "helt", "ganske", "nesten", "fortsatt", "ennå",
        "allerede", "gjerne", "kanskje", "sikkert", "virkelig", "sant",
        "hvert", "daglig", "raskt", "sakte", "sterkt", "svakt", "dypt",
        "tidlig", "sent", "langt", "nært", "vekk", "bort", "fram", "frem",
        "tilbake", "sammen", "alene", "aldri"
    )

    /** Common Norwegian verbs, present tense (finite). */
    private val NO_VERB_PRESENT = setOf(
        "er", "har", "gjør", "går", "kommer", "ser", "sier", "tar", "gir",
        "får", "vet", "tror", "vil", "kan", "skal", "må", "lærer", "trener",
        "spiser", "drikker", "sover", "leser", "skriver", "snakker", "hører",
        "føler", "tenker", "liker", "elsker", "hater", "trenger", "bruker",
        "lager", "bygger", "kjøper", "selger", "jobber", "arbeider", "leker",
        "spiller", "løper", "svømmer", "kjører", "flyr", "sitter", "står",
        "ligger", "bor", "venter", "finner", "mister", "husker", "glemmer",
        "prøver", "klarer", "begynner", "slutter", "hjelper", "viser",
        "forstår", "lytter", "velger", "åpner", "lukker", "sender", "mottar",
        "betaler", "koster", "varer", "skjer", "blir", "gjelder", "handler",
        "møter", "reiser", "drar", "henter", "bærer", "kaster", "fanger",
        "vinner", "taper", "kjemper", "beskytter", "stoler", "frykter"
    )

    /** Common Norwegian verbs, past tense (finite). */
    private val NO_VERB_PAST = setOf(
        "var", "hadde", "gjorde", "gikk", "kom", "så", "sa", "tok", "ga",
        "fikk", "visste", "trodde", "ville", "kunne", "skulle", "måtte",
        "lærte", "trente", "spiste", "drakk", "sov", "leste", "skrev",
        "snakket", "hørte", "følte", "tenkte", "likte", "elsket", "hatet",
        "trengte", "brukte", "laget", "bygde", "kjøpte", "solgte", "jobbet",
        "arbeidet", "lekte", "spilte", "løp", "svømte", "kjørte", "fløy",
        "satt", "sto", "stod", "lå", "bodde", "ventet", "fant", "mistet",
        "husket", "glemte", "prøvde", "klarte", "begynte", "sluttet",
        "hjalp", "viste", "forsto", "forstod", "lyttet", "valgte", "åpnet",
        "lukket", "sendte", "mottok", "betalte", "kostet", "varte", "skjedde",
        "ble", "gjaldt", "handlet", "møtte", "reiste", "dro", "hentet",
        "bar", "kastet", "fanget", "vant", "tapte", "kjempet"
    )

    /** Common Norwegian infinitives. */
    private val NO_VERB_INF = setOf(
        "være", "ha", "gjøre", "gå", "komme", "se", "si", "ta", "gi", "få",
        "vite", "tro", "lære", "trene", "spise", "drikke", "sove", "lese",
        "skrive", "snakke", "høre", "føle", "tenke", "like", "elske", "hate",
        "trenge", "bruke", "lage", "bygge", "kjøpe", "selge", "jobbe",
        "arbeide", "leke", "spille", "løpe", "svømme", "kjøre", "fly",
        "sitte", "stå", "ligge", "bo", "vente", "finne", "miste", "huske",
        "glemme", "prøve", "klare", "begynne", "slutte", "hjelpe", "vise",
        "forstå", "lytte", "velge", "åpne", "lukke", "sende", "motta",
        "betale", "koste", "vare", "skje", "bli", "gjelde", "handle",
        "møte", "reise", "dra", "hente", "bære", "kaste", "fange",
        "vinne", "tape", "kjempe", "beskytte", "stole", "frykte"
    )

    // ------------------------------------------------------------------ English

    private val EN_PRON = setOf(
        "i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself",
        "he", "him", "his", "himself", "she", "her", "hers", "herself",
        "it", "its", "itself", "we", "us", "our", "ours", "ourselves",
        "they", "them", "their", "theirs", "themselves", "one", "oneself",
        "who", "whom", "whose", "someone", "anyone", "everyone", "nobody",
        "something", "anything", "everything", "nothing"
    )

    private val EN_MODAL = setOf(
        "will", "would", "shall", "should", "can", "could", "may", "might",
        "must", "ought", "'ll", "'d", "cannot", "wont", "gotta", "need"
    )

    private val EN_AUX = setOf(
        "am", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "'m", "'re", "'s", "'ve"
    )

    private val EN_SUBORD = setOf(
        "if", "when", "whenever", "because", "since", "although", "though",
        "while", "whilst", "unless", "until", "till", "before", "after",
        "that", "which", "whereas", "as", "so", "once", "whether", "lest"
    )

    private val EN_COORD = setOf("and", "but", "or", "nor", "yet", "for")

    private val EN_NEG = setOf("not", "n't", "never", "no")

    private val EN_PREP = setOf(
        "in", "on", "at", "to", "from", "with", "of", "by", "under", "over",
        "between", "through", "against", "without", "about", "into", "onto",
        "upon", "within", "among", "amongst", "across", "around", "behind",
        "beside", "beyond", "during", "despite", "toward", "towards",
        "near", "off", "out", "up", "down", "per", "via", "like"
    )

    private val EN_DET = setOf(
        "the", "a", "an", "this", "that", "these", "those", "each", "every",
        "all", "both", "some", "any", "many", "much", "few", "several",
        "another", "other", "such", "either", "neither", "enough", "most"
    )

    private val EN_ADV = setOf(
        "always", "often", "seldom", "rarely", "very", "quite", "little",
        "hard", "well", "badly", "now", "here", "there", "home", "outside",
        "inside", "soon", "again", "also", "only", "just", "almost", "still",
        "yet", "already", "maybe", "perhaps", "surely", "really", "truly",
        "daily", "quickly", "slowly", "strongly", "deeply", "early", "late",
        "far", "away", "back", "together", "alone", "today", "tomorrow",
        "yesterday", "then", "once", "twice", "much", "too", "so", "ever"
    )

    /** Common English verbs, base/infinitive form. */
    private val EN_VERB_BASE = setOf(
        "be", "have", "do", "go", "come", "see", "say", "take", "give", "get",
        "know", "think", "learn", "train", "eat", "drink", "sleep", "read",
        "write", "speak", "talk", "hear", "feel", "like", "love", "hate",
        "need", "use", "make", "build", "buy", "sell", "work", "play", "run",
        "swim", "drive", "fly", "sit", "stand", "lie", "live", "wait", "find",
        "lose", "remember", "forget", "try", "manage", "begin", "start",
        "stop", "help", "show", "understand", "listen", "choose", "open",
        "close", "send", "receive", "pay", "cost", "last", "happen", "become",
        "meet", "travel", "leave", "fetch", "carry", "throw", "catch", "win",
        "fight", "protect", "trust", "fear", "want", "wish", "hope", "seem",
        "look", "let", "keep", "put", "bring", "call", "ask", "tell", "turn"
    )

    /** Common English verbs, 3rd person singular present. */
    private val EN_VERB_S = EN_VERB_BASE.map {
        when {
            it.endsWith("y") && it.length > 1 && it[it.length - 2] !in "aeiou" ->
                it.dropLast(1) + "ies"
            it.endsWith("s") || it.endsWith("x") || it.endsWith("z") ||
                it.endsWith("ch") || it.endsWith("sh") -> it + "es"
            it == "be" -> "is"
            it == "have" -> "has"
            it == "do" -> "does"
            it == "go" -> "goes"
            else -> it + "s"
        }
    }.toSet()

    /** Common English verbs, simple past. Irregulars listed explicitly. */
    private val EN_VERB_PAST = setOf(
        "was", "were", "had", "did", "went", "came", "saw", "said", "took",
        "gave", "got", "knew", "thought", "learned", "learnt", "trained",
        "ate", "drank", "slept", "read", "wrote", "spoke", "talked", "heard",
        "felt", "liked", "loved", "hated", "needed", "used", "made", "built",
        "bought", "sold", "worked", "played", "ran", "swam", "drove", "flew",
        "sat", "stood", "lay", "lived", "waited", "found", "lost",
        "remembered", "forgot", "tried", "managed", "began", "started",
        "stopped", "helped", "showed", "understood", "listened", "chose",
        "opened", "closed", "sent", "received", "paid", "cost", "lasted",
        "happened", "became", "met", "travelled", "traveled", "left",
        "fetched", "carried", "threw", "caught", "won", "fought", "protected",
        "trusted", "feared", "wanted", "wished", "hoped", "seemed", "looked",
        "kept", "put", "brought", "called", "asked", "told", "turned"
    )

    // ------------------------------------------------------------------ Lookup

    fun isPronoun(w: String, l: Lang) = if (l == Lang.NO) w in NO_PRON else w in EN_PRON
    fun isModal(w: String, l: Lang) = if (l == Lang.NO) w in NO_MODAL else w in EN_MODAL
    fun isAux(w: String, l: Lang) = if (l == Lang.NO) w in NO_AUX else w in EN_AUX
    fun isSubordinator(w: String, l: Lang) = if (l == Lang.NO) w in NO_SUBORD else w in EN_SUBORD
    fun isCoordinator(w: String, l: Lang) = if (l == Lang.NO) w in NO_COORD else w in EN_COORD
    fun isNegation(w: String, l: Lang) = if (l == Lang.NO) w in NO_NEG else w in EN_NEG
    fun isPreposition(w: String, l: Lang) = if (l == Lang.NO) w in NO_PREP else w in EN_PREP
    fun isDeterminer(w: String, l: Lang) = if (l == Lang.NO) w in NO_DET else w in EN_DET
    fun isAdverb(w: String, l: Lang) = if (l == Lang.NO) w in NO_ADV else w in EN_ADV

    fun isFiniteVerb(w: String, l: Lang) =
        if (l == Lang.NO) w in NO_VERB_PRESENT || w in NO_VERB_PAST
        else w in EN_VERB_S || w in EN_VERB_PAST

    fun isInfinitive(w: String, l: Lang) =
        if (l == Lang.NO) w in NO_VERB_INF else w in EN_VERB_BASE

    fun isInfinitiveMarker(w: String, l: Lang) =
        if (l == Lang.NO) w == "å" else w == "to"

    /** Any word the lexicon recognises at all. Used to decide recapitalisation. */
    fun isKnownWord(w: String, l: Lang): Boolean = if (l == Lang.NO) {
        w in NO_PRON || w in NO_MODAL || w in NO_AUX || w in NO_SUBORD ||
            w in NO_COORD || w in NO_NEG || w in NO_PREP || w in NO_DET ||
            w in NO_ADV || w in NO_VERB_PRESENT || w in NO_VERB_PAST ||
            w in NO_VERB_INF || w == "å"
    } else {
        w in EN_PRON || w in EN_MODAL || w in EN_AUX || w in EN_SUBORD ||
            w in EN_COORD || w in EN_NEG || w in EN_PREP || w in EN_DET ||
            w in EN_ADV || w in EN_VERB_BASE || w in EN_VERB_S || w in EN_VERB_PAST
    }

    /**
     * Names that keep their capital wherever they land.
     *
     * Case is the one thing movement changes, and one sentence carries no
     * evidence for whether a leading capital is a name or just the start of
     * the sentence. A gazetteer is how this is actually decided; the list is
     * scoped to the domain rather than trying to be a general name database.
     */
    private val PROPER_NOUNS = setOf(
        // Deliberately excludes names that collide with ordinary Norwegian
        // words: han (he), ren (clean), finn (find), rey/poe/maul/solo.
        "yoda", "luke", "leia", "vader", "anakin", "obi-wan", "kenobi",
        "chewbacca", "palpatine", "sidious", "windu", "qui-gon", "jinn",
        "padmé", "padme", "amidala", "lando", "boba", "jango", "fett",
        "ahsoka", "tano", "grogu", "dooku", "grievous", "jabba",
        "tatooine", "coruscant", "naboo", "endor", "hoth", "dagobah",
        "alderaan", "kashyyyk", "mustafar",
        "mats", "norge", "noreg", "norway", "oslo", "bergen", "trondheim",
        "sverige", "sweden", "danmark", "denmark", "england", "america",
        "linux", "android", "google", "claude",
        // Recurring Tatoeba names, the corpus source. Norwegian unknown words
        // lose a moved capital, so a name missing here would print in lower case.
        "tom", "mary", "john", "sami", "yumi", "yanni", "ziri", "ken", "mike",
        "marie", "maria", "anna", "dan", "mennad", "layla", "skura", "rima",
        "fadil", "baya", "ivan", "dmitri", "bruno", "paul", "kalman", "gabor",
        "claudio", "boris", "daniel", "adriano", "santiago", "alberto",
        "damiano", "leon", "miroslav", "gustavo", "yuri", "james", "igor",
        "elias", "lajos", "lukas", "jim", "gabriel", "fyodor", "william",
        "leonid", "vladimir", "jack", "alice", "bob", "jane", "peter",
        "michael", "david", "sarah", "lisa", "taro", "emily", "tatoeba",
        // Common Norwegian first names. "per" (per dag) and "hans" (his) are
        // left out because both are ordinary words.
        "ola", "kari", "nora", "emma", "ingrid", "lars", "knut", "jonas",
        "sofie", "ole", "anne", "erik", "nils", "jens", "eva", "marit",
        "silje", "martin", "henrik", "jakob", "sara", "ida", "thomas",
        // Places.
        "tyskland", "italia", "frankrike", "spania", "japan", "kina",
        "russland", "amerika", "finland", "island", "estland", "europa",
        "afrika", "asia", "london", "paris", "berlin", "zürich", "algerie",
        "algeria", "china", "russia", "france", "germany", "italy", "spain",
        "canada", "australia", "boston", "tokyo", "kabylie",
        // "Gud" is capitalised in Norwegian when it means God.
        "gud"
    )

    fun isProperNoun(w: String) = w in PROPER_NOUNS

    /**
     * Norwegian definite and genitive endings. Names do not take these, so a
     * sentence-initial capital carrying one is a common noun: "Barnets sinn",
     * "Kraften er sterk".
     */
    private val NO_NOUN_SUFFIXES = listOf(
        "ets", "ens", "ene", "enes", "ets", "et", "en", "ane", "a"
    )

    fun hasNorwegianNounInflection(w: String): Boolean =
        w.length >= 5 && NO_NOUN_SUFFIXES.any { w.endsWith(it) }

    /**
     * True when a word is the bare stem of something the lexicon knows:
     * "frykt" -> "frykte"/"frykter". Catches sentence-initial common nouns
     * that share a root with a listed verb, which names do not.
     */
    fun isKnownStem(w: String, l: Lang): Boolean {
        if (w.length < 3) return false
        val endings = if (l == Lang.NO)
            listOf("e", "er", "en", "et", "te", "de")
        else
            listOf("s", "es", "ed", "e")
        return endings.any { isKnownWord(w + it, l) }
    }

    /** Norwegian-only marker words, for language detection. */
    val NO_MARKERS: Set<String> = NO_PRON + NO_MODAL + NO_AUX + NO_SUBORD +
        NO_COORD + NO_NEG + NO_PREP + NO_DET

    /** English-only marker words, for language detection. */
    val EN_MARKERS: Set<String> = EN_PRON + EN_MODAL + EN_AUX + EN_SUBORD +
        EN_COORD + EN_NEG + EN_PREP + EN_DET
}
