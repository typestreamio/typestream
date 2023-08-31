package io.typestream.tools.command

import io.typestream.testing.avro.buildAuthor
import io.typestream.testing.avro.buildBook
import io.typestream.testing.avro.buildRating
import io.typestream.testing.avro.buildUser
import io.typestream.testing.avro.toProducerRecords
import io.typestream.testing.kafka.AdminClientWrapper
import io.typestream.testing.kafka.KafkaProducerWrapper
import io.typestream.tools.KafkaClustersConfig

fun seed(kafkaClustersConfig: KafkaClustersConfig) {
    val kafkaConfig = kafkaClustersConfig.clusters["local"]
    requireNotNull(kafkaConfig) {
        "local kafka cluster not found"
    }

    val adminClientWrapper = AdminClientWrapper(kafkaConfig.bootstrapServers)

    adminClientWrapper.createTopics("authors", "books", "users", "page_views", "ratings")

    val kafkaProducer = KafkaProducerWrapper(kafkaConfig.bootstrapServers, kafkaConfig.schemaRegistryUrl)

    val emily = buildAuthor("Emily St. John Mandel")
    val stationEleven = buildBook("Station Eleven", 300, emily.id)
    val seaOfTranquility = buildBook("Sea of Tranquility", 400, emily.id)
    val theGlassHotel = buildBook("The Glass Hotel", 500, emily.id)

    val olivia = buildAuthor("Octavia E. Butler")
    val kindred = buildBook("Kindred", 200, olivia.id)
    val bloodChild = buildBook("Bloodchild", 100, olivia.id)
    val parableOfTheSower = buildBook("Parable of the Sower", 200, olivia.id)
    val parableOfTheTalents = buildBook("Parable of the Talents", 200, olivia.id)

    val chimamanda = buildAuthor("Chimamanda Ngozi Adichie")
    val americanah = buildBook("Americanah", 200, chimamanda.id)
    val halfOfAYellowSun = buildBook("Half of a Yellow Sun", 250, chimamanda.id)
    val purpleHibiscus = buildBook("Purple Hibiscus", 300, chimamanda.id)

    val grace = buildUser("Grace Hopper")
    val margaret = buildUser("Margaret Hamilton")

    val authors = toProducerRecords("authors", emily, olivia, chimamanda)
    val books = toProducerRecords(
        "books",
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
    val users = toProducerRecords("users", grace, margaret)

    val pageViews =
        toProducerRecords("page_views", buildPageView(stationEleven.id)) { v -> v.get("book_id").toString() }

    val ratings = toProducerRecords(
        "ratings",
        buildRating(grace.id, stationEleven.id, 5),
        buildRating(grace.id, seaOfTranquility.id, 5),
        buildRating(grace.id, theGlassHotel.id, 5),
        buildRating(grace.id, kindred.id, 5),
        buildRating(grace.id, americanah.id, 5),
        buildRating(grace.id, halfOfAYellowSun.id, 5),
        buildRating(grace.id, purpleHibiscus.id, 5),
        buildRating(margaret.id, kindred.id, 5),
        buildRating(margaret.id, americanah.id, 5),
        buildRating(margaret.id, bloodChild.id, 5),
        buildRating(margaret.id, stationEleven.id, 5),
        buildRating(margaret.id, parableOfTheSower.id, 5),
        buildRating(margaret.id, parableOfTheTalents.id, 5),
    ) { r -> r.get("user_id").toString() + r.get("book_id").toString() }

    kafkaProducer.produce(authors)
    kafkaProducer.produce(books)
    kafkaProducer.produce(users)
    kafkaProducer.produce(ratings)
    kafkaProducer.produce(pageViews)
}
