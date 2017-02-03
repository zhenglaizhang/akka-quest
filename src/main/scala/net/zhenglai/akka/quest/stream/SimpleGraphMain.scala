package net.zhenglai.akka.quest.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ClosedShape }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source }

// Graphs are needed whenever you want to perform any kind of fan-in ("multiple inputs") or fan-out ("multiple outputs") operations.
// Considering linear Flows to be like roads, we can picture graph operations as junctions: multiple flows being connected at a single point.
object SimpleGraphMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // Graphs are built from simple Flows which serve as the linear connections within the graphs as well as junctions which serve as fan-in and fan-out points for Flows.

  // Akka Streams currently provide these junctions
  // Fan-out
  //  Broadcast[T] => 1 -> N
  //  Balance[T]   => 1 -> N
  //  UnzipWith[In, A, B, ...] => 1 -> N
  //  Unzip[A, B] => 1 -> 2, splits a stream of (A, B) tuples into 2 streams


  // Fan-in
  //  Merge[In] => N -> 1, randomly picked from inputs
  //  MergePreferred[In], => N -> 1
  //  ZipWith[A, B, ..., Out]  => N -> 1
  //  Zip[A, B] => 2 -> 1
  //  Concat[A] => 2 -> 1, concatenate 2 streams (first consume one, then the second one)


  // Such graph is simple to translate to the Graph DSL since each linear element corresponds to a Flow,
  // and each circle corresponds to either a Junction or a Source or Sink if it is beginning or ending a Flow.
  // Junction reference equality defines graph node equality (i.e. the same merge instance used in a GraphDSL refers to the same location in the resulting graph).

  // Once the GraphDSL has been constructed though, the GraphDSL instance is immutable, thread-safe, and freely shareable.
  // The same is true of all graph pieces—sources, sinks, and flows
  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    // bring in ~> & <~
    val in = Source(1 to 10)
    val out = Sink.foreach(println)

    //  builder.add(...), an operation that will make a copy of the blueprint that is passed to it and return the inlets and outlets of the resulting copy so that they can be wired up.

    // import broadcast junction
    val bcast = builder.add(Broadcast[Int](2))

    // import merge junction
    val merge = builder.add(Merge[Int](2))

    val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

    //  the GraphDSL.Builder object is mutable. It is used (implicitly) by the ~> operator, also making it a mutable operation as well.
    in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
    bcast ~> f4 ~> merge
    ClosedShape
  })

  g.run()


  val topHeadSink = Sink.head[Int]
  val bottomHeadSink = Sink.head[Int]
  val sharedDoubler = Flow[Int].map(_ * 2)

  // importing using builder.add(...) ignores the materialized value of the imported graph
  // while importing via the factory method allows its inclusion
  RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit b =>
    (topHS, bottomHS) =>
      import GraphDSL.Implicits._
      val bcast = b.add(Broadcast[Int](2))
      Source.single(1) ~> bcast.in

      bcast.out(0) ~> sharedDoubler ~> topHS.in
      bcast.out(1) ~> sharedDoubler ~> bottomHS.in
      ClosedShape
  })

  Thread.sleep(1000)
  system.terminate()
}
