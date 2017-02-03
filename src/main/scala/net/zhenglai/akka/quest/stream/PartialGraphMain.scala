package net.zhenglai.akka.quest.stream

import scala.concurrent.{ Await, Future }

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip, ZipWith }
import scala.concurrent.duration._

import akka.util.Timeout

// Source is a partial graph with exactly one output, that is it returns a SourceShape.
// Sink is a partial graph with exactly one input, that is it returns a SinkShape.
// Flow is a partial graph with exactly one input and exactly one output, that is it returns a FlowShape.

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


  val pairs = Source.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    val zip = b.add(Zip[Int, Int]())

    def ints = Source.fromIterator(() => Iterator.from(1))


    // connect the graph
    ints.filter(_ % 2 != 0) ~> zip.in0
    ints.filter(_ % 2 == 0) ~> zip.in1

    // expose port
    SourceShape(zip.out)
  })

  val firstPair: Future[(Int, Int)] = pairs.runWith(Sink.head)

  println(s"firstPair = ${Await.result(firstPair, 200.millis)}")


  val pairUpWithToString =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // prepare graph elements
      val bcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, String]())

      // connect the graph
      bcast.out(0).map { x => println(x); identity(x) } ~> zip.in0
      bcast.out(1).map { x => println(x); x.toString } ~> zip.in1

      // expose ports
      FlowShape(bcast.in, zip.out)
    })

  // tuple unpacking!!
  val (_, f) = pairUpWithToString.runWith(Source(List(1, 2, 3)), Sink.last)
  println(s"f = ${Await.result(f, 200.millis)}")

  val s1 = Source(List(1))
  val s2 = Source(List(2, 2))
  val merged = Source.combine(s1, s2)(Merge(_, eagerComplete = false))
  val mr: Future[Int] = merged.runWith(Sink.fold(0)(_ + _))
  println(s"mr = ${Await.result(mr, 200.millis)}")



  implicit val timeout = Timeout(200.millis)
  // TODO: fix it
  val actorRef = Await.result(system.actorSelection("some/remote/actor").resolveOne(), 200.millis) // remote actorRef
  val sendRemotely = Sink.actorRef(actorRef, "Done")
  val localProcessing = Sink.foreach[Int](_ => /* do something useful */())
  val sink = Sink.combine(sendRemotely, localProcessing)(Broadcast[Int](_))
  Source(List(1, 2, 3)).runWith(sink)

  Thread.sleep(1000)
  system.terminate()
}
