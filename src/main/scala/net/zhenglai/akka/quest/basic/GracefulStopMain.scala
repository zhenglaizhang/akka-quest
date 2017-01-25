package net.zhenglai.akka.quest.basic

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

import akka.actor.{ Actor, ActorLogging, ActorSystem, PoisonPill, Props, Terminated }
import akka.pattern.gracefulStop
import akka.pattern.ask
import akka.util.Timeout

class Cruncher extends Actor with ActorLogging {
  override def receive = {
    case msg => log.info("received msg: {}", msg)
  }
}

class Manager extends Actor with ActorLogging {

  import Manager._

  val worker = context.watch(context.actorOf(Props[Cruncher], "worker"))

  override def receive = {
    case "job" => worker ! "crunch"
    case Shutdown =>
      worker ! PoisonPill
      // todo: repro the path
      context become shuttingDown
  }

  def shuttingDown: Receive = {
    case "job" => sender() ! "Service Unavailable, shutting down"
    case Terminated(`worker`) =>
      context stop self
  }
}

object Manager {

  case object Shutdown

}

object GracefulStopMain extends App {

  val system = ActorSystem(name = "gracefulStopMain")
  val manager = system.actorOf(Props[Manager], name = "manager")
  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(10 seconds)
  // When gracefulStop() returns successfully, the actor’s postStop() hook will have been executed:
  // there exists a happens-before edge between the end of postStop() and the return of gracefulStop().
  try {

    Future {Iterator.range(0, 5).foreach { _ => manager ! "job" }}
    val stopped: Future[Boolean] = gracefulStop(manager, 5 seconds, Manager.Shutdown)
    Future
      .sequence(Iterator
        .range(0, 5)
        .map { _ => manager ? "job" }
      )
      .foreach(system.log.info("job?: {}", _))
    system.log.info("graceful stopping result: {}", Await.result(stopped, 6 seconds))
  } catch {
    case e: akka.pattern.AskTimeoutException =>
      system.log.error(e, "non gracefully stopped.")
  }

  Thread.sleep(1000)
  system.terminate()
  sys.exit()
}