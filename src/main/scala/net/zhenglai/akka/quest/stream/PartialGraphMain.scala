package net.zhenglai.akka.quest.stream

import scala.concurrent.{ Await, Future }

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ClosedShape, UniformFanInShape }
import akka.stream.scaladsl.{ GraphDSL, RunnableGraph, Sink, Source, ZipWith }
import scala.concurrent.duration._

object PartialGraphMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // Making a Graph a RunnableGraph requires all ports to be connected
  // For a specialized element that given 3 inputs will pick the greatest int value of each zipped triple
  val pickMaxOfThree = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val zip1 = b.add(ZipWith[Int, Int, Int](math.max))
    val zip2 = b.add(ZipWith[Int, Int, Int](math.max))

    zip1.out ~> zip2.in0
    UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
  }

  val resultSink = Sink.head[Int]

  // GraphDSL is not able to provide compile time type-safety about whether or not all elements have been properly connected—this validation is performed as a runtime check during the graph's instantiation.
//A partial graph also verifies that all ports are either connected or part of the returned Shape.
  val g = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b =>
    sink =>
      import GraphDSL.Implicits._
      // importing the partial graph will return its shape (inlets & outlets)
      val pm3 = b.add(pickMaxOfThree)
      Source.single(1) ~> pm3.in(0)
      Source.single(3) ~> pm3.in(1)
      Source.single(2) ~> pm3.in(2)
      pm3.out ~> sink.in
      ClosedShape
  })

  val max: Future[Int] = g.run()
  println(Await.result(max, 200.millis))

  Thread.sleep(1000)
  system.terminate()
}
