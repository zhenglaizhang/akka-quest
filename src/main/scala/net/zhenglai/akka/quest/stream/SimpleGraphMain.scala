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
  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._ // bring in ~> & <~
    val in = Source(1 to 10)
    val out = Sink.foreach(println)

    val bcast = builder.add(Broadcast[Int](2))
    val merge = builder.add(Merge[Int](2))

    val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

    //  the GraphDSL.Builder object is mutable. It is used (implicitly) by the ~> operator, also making it a mutable operation as well.
    in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
    bcast ~> f4 ~> merge
    ClosedShape
  })

  g.run()

  Thread.sleep(1000)
  system.terminate()
}
