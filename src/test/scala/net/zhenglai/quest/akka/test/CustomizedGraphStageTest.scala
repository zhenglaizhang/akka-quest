package net.zhenglai.quest.akka.test

import java.nio.ByteOrder

import scala.concurrent.Await

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{ BidiFlow, Flow, GraphDSL, Sink, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.util.ByteString
import org.scalatest.FunSuite
import scala.concurrent.duration._

import akka.actor.ActorSystem

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
}
