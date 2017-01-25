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
      context become shuttingDown
  }

  def shuttingDown: Receive = {
    case "job" => sender() ! "Service Unavailable, shutting down"
    case Terminated(`worker`) =>
      log.info("terminated msg got")
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

  // Keep in mind that an actor stopping and its name being deregistered are separate events which happen asynchronously from each other.
  // Therefore it may be that you will find the name still in use after gracefulStop() returned.
  // In order to guarantee proper deregistration, only reuse names from within a supervisor you control and only in response to a Terminated message, i.e. not for top-level actors.
  try {

    Future {Iterator.range(0, 5).foreach { _ => manager ! "job" }}
    val stopped: Future[Boolean] = gracefulStop(manager, 5 seconds, Manager.Shutdown)
    val r = Await.result(Future.sequence(Iterator.range(0, 5).map { _ => manager ? "job" }), 2 seconds)
    system.log.warning("service unavailable?: {}", r.toList)
    system.log.info("graceful stopping result: {}", Await.result(stopped, 6 seconds))
  } catch {
    case e: akka.pattern.AskTimeoutException =>
      system.log.error(e, "non gracefully stopped.")
  }

  Thread.sleep(1000)
  system.terminate()
  sys.exit()
}
