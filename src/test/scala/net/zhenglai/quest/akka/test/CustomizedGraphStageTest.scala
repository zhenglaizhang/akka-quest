package net.zhenglai.quest.akka.test

import java.nio.ByteOrder

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, Flow, GraphDSL, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import org.scalatest.FunSuite

// bidirectional flows
//  codec stage
//  framing protocol
// BidiFlow which is a graph that has exactly two open inlets and two open outlets
class CustomizedGraphStageTest extends FunSuite {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  test("bidiflow intro") {
    sealed trait Message
    final case class Ping(id: Int) extends Message
    final case class Pong(id: Int) extends Message

    def toBytes(msg: Message): ByteString = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      msg match {
        case Ping(id) => ByteString.newBuilder.putByte(1).putInt(id).result()
        case Pong(id) => ByteString.newBuilder.putByte(2).putInt(id).result()
      }
    }

    def fromBytes(bytes: ByteString): Message = {
      implicit val order = ByteOrder.LITTLE_ENDIAN
      val it = bytes.iterator
      it.getByte match {
        case 1 => Ping(it.getInt)
        case 2 => Pong(it.getInt)
        case other => throw new RuntimeException(s"parse error: expected 1|2, got $other")
      }
    }

    val codec = BidiFlow.fromFunctions(toBytes, fromBytes)

    val codecVerbose = BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val outbound = b.add(Flow[Message].map(toBytes))
      val inbound = b.add(Flow[ByteString].map(fromBytes))
      BidiShape.fromFlows(outbound, inbound)
    })

    // simply integrate any other serialization library that turns an object into a sequence of bytes
  }

  // todo http://doc.akka.io/docs/akka/2.4/scala/stream/stream-customize.html#graphstage-scala
  test("customized NumbersSource") {
    class NumbersSource extends GraphStage[SourceShape[Int]] {
      // define the (sole) output port of this stage
      val out: Outlet[Int] = Outlet("NumbersSource")


      override def shape: SourceShape[Int] = SourceShape(out)

      // where the actual (possibly stateful) logic will live
      @scala.throws[Exception](classOf[Exception])
      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // All state MUST be inside the GraphStageLogic,
        // never inside the enclosing GraphStage.
        // This state is safe to access and modify from all the
        // callbacks that are provided by GraphStageLogic and the
        // registered handlers.
        private var counter = 1
        setHandler(out, new OutHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPull(): Unit = {
            push(out, counter)
            // complete(out)
            // fail(err)
            counter += 1
          }
        })
      }
    }

    val sourceGraph: Graph[SourceShape[Int], NotUsed] = new NumbersSource

    val mySource: Source[Int, NotUsed] = Source.fromGraph(sourceGraph)

    val r1 = mySource.take(10).runFold(0)(_ + _)
    val r2 = mySource.take(100).runFold(0)(_ + _)
    println(Await.result(r1, 1 second)) // 55
    println(Await.result(r2, 1 second)) // 5050
  }

  test("customized StdoutSink") {
    class StdoutSink extends GraphStage[SinkShape[Int]] {
      val in: Inlet[Int] = Inlet("StdoutSink")
      override val shape: SinkShape[Int] = SinkShape(in)

      override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        // requests on element at the Sink startup
        override def preStart() = if (!hasBeenPulled(in)) pull(in)

        setHandler(in, new InHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPush() = {
            println(grab(in))
            pull(in)
          }
        })
      }
    }

    val sinkGraph: Graph[SinkShape[Int], NotUsed] = new StdoutSink
    val stdoutSink: Sink[Int, NotUsed] = Sink.fromGraph(sinkGraph)
    Source(List(1, 2, 3)).runWith(stdoutSink)
  }

  test("customized map") {
    class Map[A, B](f: A => B) extends GraphStage[FlowShape[A, B]] {
      val in = Inlet[A]("Map.in")
      val out = Outlet[B]("Map.out")

      override val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPush() = {
            push(out, f(grab(in)))
          }
        })

        setHandler(out, new OutHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPull() = {
            pull(in)
          }
        })
      }
    }

    def flowGraph[A, B](f: A => B): Graph[FlowShape[A, B], NotUsed] = new Map[A, B](f)

    def mapFlow[A, B](f: A => B): Flow[A, B, NotUsed] = Flow.fromGraph(flowGraph(f))

    val sub = Source(1 to 2)
      .via(mapFlow(_ * 2))
      .runWith(TestSink.probe)
    sub.request(4)
      .expectNextN(List(2, 4))
  }

  test("customized filter") {
    class Filter[A](p: A => Boolean) extends GraphStage[FlowShape[A, A]] {

      val in = Inlet[A]("Filter.in")
      val out = Outlet[A]("Filter.in")

      override def shape = FlowShape.of(in, out)

      @scala.throws[Exception](classOf[Exception])
      override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPush() = {
            val elem = grab(in)
            if (p(elem)) push(out, elem)
            else {
              println("filtering out: " + elem)
              pull(in)
            }
          }
        })

        setHandler(out, new OutHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPull() = pull(in)
        })
      }
    }

    def flowGraph[A](p: A => Boolean): Graph[FlowShape[A, A], NotUsed] = new Filter(p)

    def filterFlow[A](p: A => Boolean) = Flow.fromGraph(flowGraph(p))

    val sub = Source(1 to 5)
      .via(filterFlow(_ % 2 != 0))
      .runWith(TestSink.probe)
    sub.request(10)
      .expectNextN(List(1, 3, 5))
  }

  test("customized Duplicator") {
    class Duplicator[A] extends GraphStage[FlowShape[A, A]] {
      val in = Inlet[A]("Duplicator.in")
      val out = Outlet[A]("Dupcator.out")

      @scala.throws[Exception](classOf[Exception])
      override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        var lastElem: Option[A] = None

        setHandler(in, new InHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPush() = {
            val elem = grab(in)
            lastElem = Some(elem)
            push(out, elem)
          }



          // Completion handling usually (but not exclusively) comes into
          // the picture when processing stages need to emit a few more
          // elements after their upstream source has been completed.
          // todo: understand it!!
          // handle the case where the upstream closes while the stage still has
          // elements it wants to push downstream.
          override def onUpstreamFinish() = {
            if (lastElem.isDefined) emit(out, lastElem.get)
            complete(out)
          }
        })

        setHandler(out, new OutHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPull() = {
            if (lastElem.isDefined) {
              push(out, lastElem.get)
              lastElem = None
            } else {
              pull(in)
            }
          }
        })
      }

      override def shape = FlowShape.of(in, out)
    }

    class Duplicator2[A] extends GraphStage[FlowShape[A, A]] {
      val in = Inlet[A]("Duplicator.in")
      val out = Outlet[A]("Duplicator.out")

      val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        setHandler(in, new InHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPush() = {
            val elem = grab(in)

            // this will temporarily suspend this handler until the two elems are
            // emitted and then reinstates it
            emitMultiple(out, scala.collection.immutable.Iterable(elem, elem))
          }
        })

        setHandler(out, new OutHandler {
          @scala.throws[Exception](classOf[Exception])
          override def onPull() = {
            pull(in)
          }
        })
      }
    }

    val sub = Source(3 to 5)
      .via(new Duplicator2[Int])
      .runWith(TestSink.probe)

    sub.request(10)
      .expectNextN(List(3, 3, 4, 4, 5, 5))
  }
}
