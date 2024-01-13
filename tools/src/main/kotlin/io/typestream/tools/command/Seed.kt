package io.typestream.tools.command

import io.typestream.config.Config
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.testing.model.Author
import io.typestream.testing.model.Book
import io.typestream.testing.model.PageView
import io.typestream.testing.model.Rating
import io.typestream.testing.model.User

fun seed(config: Config, args: List<String>) {
    val encoding = args.firstOrNull() ?: "avro"

    val kafkaConfig = config.sources.kafka["local"]
    requireNotNull(kafkaConfig) { "local kafka cluster not found" }

    val adminClientWrapper = AdminClientWrapper(kafkaConfig.bootstrapServers)

    adminClientWrapper.createTopics("authors", "books", "users", "page_views", "ratings")

    val kafkaProducer = KafkaProducerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistry.url)

    val emily = Author(name = "Emily St. John Mandel")
    val stationEleven = Book(title = "Station Eleven", wordCount = 300, authorId = emily.id)
    val seaOfTranquility = Book(title = "Sea of Tranquility", wordCount = 400, authorId = emily.id)
    val theGlassHotel = Book(title = "The Glass Hotel", wordCount = 500, authorId = emily.id)

    val octavia = Author(name = "Octavia E. Butler")
    val kindred = Book(title = "Kindred", wordCount = 100, authorId = octavia.id)
    val bloodChild = Book(title = "Bloodchild", wordCount = 100, authorId = octavia.id)
    val parableOfTheSower = Book(title = "Parable of the Sower", wordCount = 200, authorId = octavia.id)
    val parableOfTheTalents = Book(title = "Parable of the Talents", wordCount = 300, authorId = octavia.id)

    val chimamanda = Author(name = "Chimamanda Ngozi Adichie")
    val americanah = Book(title = "Americanah", wordCount = 200, authorId = chimamanda.id)
    val halfOfAYellowSun = Book(title = "Half of a Yellow Sun", wordCount = 250, authorId = chimamanda.id)
    val purpleHibiscus = Book(title = "Purple Hibiscus", wordCount = 300, authorId = chimamanda.id)

    val grace = User(name = "Grace Hopper")
    val margaret = User(name = "Margaret Hamilton")

    val authors = listOf(emily, octavia, chimamanda)

    val books = listOf(
        stationEleven,
        seaOfTranquility,
        theGlassHotel,
        kindred,
        bloodChild,
        parableOfTheSower,
        parableOfTheTalents,
        americanah,
        halfOfAYellowSun,
        purpleHibiscus,
    )

    val users = listOf(grace, margaret)

    val pageViews = listOf(PageView(stationEleven.id, stationEleven.id))

    val ratings = listOf(
        Rating(grace.id + stationEleven.id, grace.id, stationEleven.id, 5),
        Rating(grace.id + seaOfTranquility.id, grace.id, seaOfTranquility.id, 5),
        Rating(grace.id + theGlassHotel.id, grace.id, theGlassHotel.id, 5),
        Rating(grace.id + kindred.id, grace.id, kindred.id, 5),
        Rating(grace.id + kindred.id, grace.id, americanah.id, 5),
        Rating(grace.id + halfOfAYellowSun.id, grace.id, halfOfAYellowSun.id, 5),
        Rating(grace.id + purpleHibiscus.id, grace.id, purpleHibiscus.id, 5),
        Rating(margaret.id + kindred.id, margaret.id, kindred.id, 5),
        Rating(margaret.id + americanah.id, margaret.id, americanah.id, 5),
        Rating(margaret.id + bloodChild.id, margaret.id, bloodChild.id, 5),
        Rating(margaret.id + stationEleven.id, margaret.id, stationEleven.id, 5),
        Rating(margaret.id + parableOfTheSower.id, margaret.id, parableOfTheSower.id, 5),
        Rating(margaret.id + parableOfTheTalents.id, margaret.id, parableOfTheTalents.id, 5),
    )

    kafkaProducer.produce("authors", encoding, authors)
    kafkaProducer.produce("books", encoding, books)
    kafkaProducer.produce("users", encoding, users)
    kafkaProducer.produce("page_views", encoding, pageViews)
    kafkaProducer.produce("ratings", encoding, ratings)
}
