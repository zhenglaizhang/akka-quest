package net.zhenglai.akka.quest.stream

import scala.concurrent.Future

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source }
import akka.stream.{ ActorMaterializer, ClosedShape }

object BroadCastMain extends App {
  implicit val system: ActorSystem = ActorSystem("BroadcastMainSystem")
  implicit val ec = system.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()
  val log = Logging(system, getClass)

  // Elements that can be used to form such "fan-out" (or "fan-in") structures are referred to as "junctions" in Akka Streams.
  // One of these that we'll be using in this example is called Broadcast, and it simply emits elements from its input port to all of its output ports.


  // Akka Streams intentionally separate the linear stream structures (Flows) from the non-linear, branching ones (Graphs) in order to offer the most convenient API for both of these cases.

  final case class Author(handle: String)

  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
  }

  val akkaTag = Hashtag("#akka")

  val writeAuthors: Sink[Author, Future[Done]] =
    Sink.foreach(x => log.info(s"author: $x"))
  val writeHashtags: Sink[Hashtag, Future[Done]] =
    Sink.foreach(x => log.info(s"tag: $x"))


  val tweets: Source[Tweet, NotUsed] =
    Source.fromIterator(() =>
      List(
        Tweet(Author("a1"), 1L, "tweet1 #tag11 #tag12 "),
        Tweet(Author("a2"), 2L, "tweet2 #tag21 #tag22")
      ).toIterator
    )

  val authors: Source[Author, NotUsed] =
    tweets
      .filter(_.hashtags.contains(akkaTag))
      .map(_.author)

  authors.runWith(Sink.foreach(println))

  // Both Graph and RunnableGraph are immutable, thread-safe, and freely shareable.

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b => // implicit graph builder
    import GraphDSL.Implicits._
    // import ~>

    val bcast = b.add(Broadcast[Tweet](2))
    // ~> edge operator / connect / via / to
    tweets ~> bcast.in
    bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
    bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags
    ClosedShape // fully connected graph, closed, no unconnected inputs or outputs,
    // since it's closed, easy to transform the graph into a RunnableGraph
  })

  g.run()

  system.terminate()
}

// A graph can also have one of several other shapes, with one or more unconnected ports.
// Having unconnected ports expresses a graph that is a partial graph.
