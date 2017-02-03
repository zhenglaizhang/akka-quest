package net.zhenglai.akka.quest.stream

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source }
import akka.stream.{ ActorMaterializer, ClosedShape, OverflowStrategy }
import akka.{ Done, NotUsed }

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
    Source.fromIterator(() => (0 to 20).toIterator)
      .map(i => Tweet(Author(s"a$i"), i.toLong, s"tweet$i #tag${i}1 #tag${i}2 #akka"))

  // One of the main advantages of Akka Streams is that they always propagate back-pressure information from stream Sinks (Subscribers) to their Sources (Publishers).
  // It is not an optional feature, and is enabled at all times.
  // With Akka Streams buffering can and must be handled explicitly.
  val authors: Source[Author, NotUsed] =
  tweets
    // TODO: dive into
    .buffer(10, OverflowStrategy.dropHead) // we only care about latest 10 tweets
    .map { x => Thread.sleep(10); x }
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



  // Remember those mysterious Mat type parameters on Source[+Out, +Mat], Flow[-In, +Out, +Mat] and Sink[-In, +Mat]?
  // They represent the type of values these processing parts return when materialized
  // A RunnableGraph may be reused and materialized multiple times, because it is just the "blueprint" of the stream.

  // flow, reusable
  def countFlow[T]: Flow[T, Int, NotUsed] = Flow[T].map(_ => 1)
  val sumSink: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)
  val counterGraph: RunnableGraph[Future[Int]] =
    tweets
      .via(countFlow)
      .toMat(sumSink)(Keep.right)

  val sum: Future[Int] = counterGraph.run()
  sum.foreach(c => println(s"1st: Total $c tweets processed"))

  // runWith(someSink) short for toMat(someSink)(Keep.right).run()
  tweets.map(_ => 1).runWith(sumSink).foreach(c => println(s"2rd: Total $c tweets processed"))

  Thread.sleep(10000)
  system.terminate()
}

// A graph can also have one of several other shapes, with one or more unconnected ports.
// Having unconnected ports expresses a graph that is a partial graph.
