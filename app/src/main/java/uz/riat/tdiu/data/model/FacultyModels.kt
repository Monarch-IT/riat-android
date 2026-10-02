package uz.riat.tdiu.data.model

data class Leader(
    val id: String,
    val fullName: String,
    val role: String,
    val degree: String,
    val photo: String,
    val phone: String,
    val email: String,
    val reception: String,
    val room: String,
    val bio: String
)

data class Department(
    val id: String,
    val name: String,
    val head: String,
    val description: String,
    val programs: List<String>
)

data class FacultyData(
    val facultyName: String,
    val universityName: String,
    val leaders: List<Leader>,
    val departments: List<Department>
)

data class NewsItem(
    val id: Int,
    val title: String,
    val thumbnailUrl: String,
    val publishDate: String,
    val views: Int,
    val category: String
)
