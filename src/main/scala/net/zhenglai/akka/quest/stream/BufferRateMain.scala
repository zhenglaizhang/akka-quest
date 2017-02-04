package net.zhenglai.akka.quest.stream

import scala.util.Random

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings, Attributes, OverflowStrategy }


// When upstream and downstream rates differ, especially when the throughput has spikes, it can be useful to introduce buffers in a stream.
object BufferRateMain extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withInputBuffer(
        initialSize = 64,
        maxSize = 64
      )
  )
  implicit val ec = system.dispatcher

  // To run a stage asynchronously it has to be marked explicitly as such using the .async method.
  // Being run asynchronously means that a stage, after handing out an element to its downstream consumer is able to immediately process the next message.
  // TODO: why

  //  normal fused synchronous execution model of flows where an element completely passes through the processing pipeline before the next element enters the flow.
  Source(1 to 3)
    .map { x => println(s"A: $x"); x } //.async
    .map { x => println(s"B: $x"); x } //.async
    .map { x => println(s"C: $x"); x } //.async
    .runWith(Sink.ignore)

  // The next element is processed by an asynchronous stage as soon as it is emitted the previous one.
  Source(1 to 10)
    .map { x => println(s"\tA: $x"); x }.async
    .map { x => println(s"\tB: $x"); x }.async
    .map { x => println(s"\tC: $x"); x }.async
    .runWith(Sink.ignore)

  // To amortize this cost(passing element through the asynchronous (and therefore thread crossing) boundary) Akka Streams uses a windowed, batching backpressure strategy internally.
  //  It is windowed because as opposed to a Stop-And-Wait protocol multiple elements might be "in-flight" concurrently with requests for elements.
  // It is also batching because a new element is not immediately requested once an element has been drained from the window-buffer but multiple elements are requested after multiple elements have been drained.

  // Previously we always assumed that the rate of the processing chain is strictly coordinated through the backpressure signal causing all stages to process no faster than the throughput of the connected chain.
  // There are tools in Akka Streams however that enable the rates of different segments of a processing chain to be "detached" or to define the maximum throughput of the stream through external timing sources.

  // for performance reasons Akka Streams introduces a buffer for every asynchronous processing stage. The purpose of these buffers is solely optimization,
  // in fact the size of 1 would be the most natural choice if there would be no need for throughput improvements.
  // Therefore it is recommended to keep these buffer sizes small, and increase them only to a level suitable for the throughput requirements of the application.

  // the buffer size of this map is 1
  val section =
    Flow[Int]
      .map(_ * 2).async
      .addAttributes(Attributes.inputBuffer(initial = 1, max = 1))

  // the buffer size of this map is the default
  val flow = section.via(Flow[Int].map(_ * 2)).async


  // TODO: complete it
  // Internal buffers could cause issues
  //  case class Tick()
  //
  //  RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
  //    import GraphDSL.Implicits._
  //
  //    // this is the asyncrhonous stage in this graph
  //  })


  // In general, when time or rate driven processing stages exhibit strange behavior,
  // one of the first solutions to try should be to decrease the input buffer of the affected elements to 1.


  // summarizes fast stream of elements to a standart deviation
  // such flow's rate is decoupled.
  // The element rate at the start of the flow can be much higher that the element rate at the end of the flow.
  val statsFlow = Flow[Double]
    .conflateWithSeed(Seq(_))(_ :+ _)
    .map { s =>
      val u = s.sum / s.size
      val se = s.map(x => math.pow(x - u, 2))
      val o = math.sqrt(se.sum / se.size)
      (o, u, s.size)
    }

  // how conflate can be used to implement random
  // drop of elements when consumer is not able to keep up with the producer.
  val p = 0.01
  val sampleFlow = Flow[Double]
    .conflateWithSeed(Seq(_)) {
      case (acc, elem) if Random.nextDouble < p => acc :+ elem
      case (acc, _) => acc
    }
    .mapConcat(_.toList)

  val s = Source(100 to 200)
    .map(_.toDouble)
    .via(sampleFlow)
    .map { x => Thread.sleep(100); identity(x) }
    .runForeach(println)
  // TODO: how to simulate slow consumer?


  // EXPANDING
  // Expand helps to deal with slow producers which are unable to keep up with the demand coming from consumers.
  // Expand allows to extrapolate a value to be sent as an element to a consumer.
  val lastFlow = Flow[Double]
    .expand(Iterator.continually(_))
  // sends the same element to consumer when producer does not send any new elements.

  Source(1 to 2)
    .map(_.toDouble)
    .via(lastFlow)
    .addAttributes(Attributes.inputBuffer(initial = 1, max = 1))
    .runForeach(x => println(s"expand: $x"))


  // tracks and reports a drift between fast consumer and slow producer
  // that all of the elements coming from upstream will go through expand at least once. This means that the output of this flow is going to report a drift of zero if producer is fast enough, or a larger drift otherwise.
  val driftFlow = Flow[Double]
    .expand(i => Iterator.from(0).map(i -> _))

  Source(1 to 100)
    .map(_.toDouble)
    .via(driftFlow)
    .addAttributes(Attributes.inputBuffer(initial = 1, max = 1))
    .runForeach(x => println(s"drift: $x"))


  // BUFFERS
  //  explicit user defined buffers that are part of the domain logic of the stream processing pipeline of an application.
  case class Job()

  def inboundJobsConnector() = ???

  // Getting a stream of jobs from an imaginary external system as a Source
  // ensure that 1000 jobs (but not more) are dequeued from an external (imaginary)
  // system and stored locally in memory - relieving the external system:
  val jobs: Source[Job, NotUsed] = inboundJobsConnector()
  jobs.buffer(1000, OverflowStrategy.backpressure)
  jobs.buffer(1000, OverflowStrategy.dropTail)
  jobs.buffer(1000, OverflowStrategy.dropNew)
  jobs.buffer(1000, OverflowStrategy.dropHead)

  // aggressive, useful when dropping jobs is preferred to delaying jobs
  jobs.buffer(1000, OverflowStrategy.dropBuffer)

  // If our imaginary external job provider is a client using our API, we might want to enforce that the client cannot have more than 1000 queued jobs otherwise we consider it flooding and terminate the connection.
  jobs.buffer(1000, OverflowStrategy.fail)

  Thread.sleep(1000)
  system.terminate()
}
