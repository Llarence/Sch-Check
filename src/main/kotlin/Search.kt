import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

typealias Option = OptionResponse

// Having all these requests hidden in by lazy could cause unexpected lag
val searchOptions by lazy {
    // Why is this capitalized, but others are snake case
    val terms = runBlocking { getTerms() }

    terms.associateWith { SearchOptions(it) }
}

class SearchOptions(val term: Option) {
    val subjects by lazy { runBlocking { getOptions("subject", term.code) } }
    val attributes by lazy { runBlocking { getOptions("attribute", term.code) } }
    val campuses by lazy { runBlocking { getOptions("campus", term.code) } }
    val levels by lazy { runBlocking { getOptions("level", term.code) } }
    val buildings by lazy { runBlocking { getOptions("building", term.code) } }
    val colleges by lazy { runBlocking { getOptions("college", term.code) } }
    val departments by lazy { runBlocking { getOptions("department", term.code) } }
    val scheduleTypes by lazy { runBlocking { getOptions("scheduleType", term.code) } }
    val durationTypes by lazy { runBlocking { getOptions("duration", term.code) } }
    val partsOfTerm by lazy { runBlocking { getOptions("partOfTerm", term.code) } }
}

enum class AMPM(private val value: String) {
    AM("AM"),
    PM("PM");

    override fun toString(): String {
        return value
    }
}

@Serializable
data class DayTime(val hour: Int, val minute: Int) {
    val amPM = if (hour < 12) { AMPM.AM } else {AMPM.PM }
    val hour12: Int
    val inMinutes = (hour * 60) + minute

    init {
        val tempHour = if (hour < 12) {
            hour
        } else {
            hour - 12
        }

        hour12 = if (tempHour == 0) {
            12
        } else {
            tempHour
        }
    }
}

// Some things could be enums, but there would be too many values (and they may change)
@Serializable
data class Search(val subject: String? = null,
                  val courseNumber: String? = null,
                  val keyword: String? = null,
                  val keywordAll: String? = null,
                  val keywordAny: String? = null,
                  val keywordExact: String? = null,
                  val keywordWithout: String? = null,
                  val attribute: String? = null,
                  val campus: String? = null,
                  val level: String? = null,
                  val building: String? = null,
                  val college: String? = null,
                  val department: String? = null,
                  val scheduleType: String? = null,
                  val durationValue: Double? = null,
                  val durationType: String? = null,
                  val partOfTerm: String? = null,
                  val courseNumberRangeLow: Int? = null,
                  val courseNumberRangeHigh: Int? = null,
                  val days: Set<DayOfWeek> = setOf(),
                  val start: DayTime? = null,
                  val end: DayTime? = null,
                  val openOnly: Boolean = false,
                  val term: String) {
    fun toQueries(): List<Query> {
        val queries = mutableListOf<Query>()

        if (subject != null) { queries.add(Query("txt_subject", subject)) }
        if (courseNumber != null) { queries.add(Query("txt_courseNumber", courseNumber.toString())) }
        if (keyword != null) { queries.add(Query("txt_keywordlike", keyword)) }
        if (keywordAll != null) { queries.add(Query("txt_keywordall", keywordAll)) }
        if (keywordAny != null) { queries.add(Query("txt_keywordany", keywordAny)) }
        if (keywordExact != null) { queries.add(Query("txt_keywordexact", keywordExact)) }
        if (keywordWithout != null) { queries.add(Query("txt_keywordwithout", keywordWithout)) }
        if (attribute != null) { queries.add(Query("txt_attribute", attribute)) }
        if (campus != null) { queries.add(Query("txt_campus", campus)) }
        if (level != null) { queries.add(Query("txt_level", level)) }
        if (building != null) { queries.add(Query("txt_building", building)) }
        if (college != null) { queries.add(Query("txt_college", college)) }
        if (department != null) { queries.add(Query("txt_department", department)) }
        if (scheduleType != null) { queries.add(Query("txt_scheduleType", scheduleType)) }
        if (durationValue != null) { queries.add(Query("txt_durationunit_value", durationValue.toString())) }
        if (durationType != null) { queries.add(Query("txt_durationunit", durationType)) }
        if (partOfTerm != null) { queries.add(Query("txt_partOfTerm", partOfTerm)) }
        if (courseNumberRangeLow != null) { queries.add(Query("txt_course_number_range", courseNumberRangeLow.toString())) }
        if (courseNumberRangeHigh != null) { queries.add(Query("txt_course_number_range_to", courseNumberRangeHigh.toString())) }
        for (day in DayOfWeek.entries) {
            if (day in days) {
                if (day == DayOfWeek.SUNDAY) {
                    queries.add(Query("chk_include_0", "true"))
                } else {
                    queries.add(Query("chk_include_$day", "true"))
                }
            }
        }
        if (start != null) {
            queries.add(Query("txt_select_start_hour", start.hour12.toString()))
            queries.add(Query("txt_select_start_min", start.minute.toString()))
            queries.add(Query("txt_select_start_ampm", start.amPM.toString()))
        }
        if (end != null) {
            queries.add(Query("txt_select_end_hour", end.hour12.toString()))
            queries.add(Query("txt_select_end_min", end.minute.toString()))
            queries.add(Query("txt_select_end_ampm", end.amPM.toString()))
        }
        if (openOnly) { queries.add(Query("chk_open_only", "true")) }
        queries.add(Query("txt_term", term))

        return queries
    }

    override fun toString(): String {
        return toQueries().joinToString("&")
    }
}
