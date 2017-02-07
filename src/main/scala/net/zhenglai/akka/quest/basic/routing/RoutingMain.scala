package net.zhenglai.akka.quest.basic.routing

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Terminated }
import akka.routing._
import akka.stream.ActorMaterializer
import scala.concurrent.duration._


// router -> routees
class Master extends Actor with ActorLogging {
  var router = {
    val routees = Vector.fill(300) {
      val r = context.actorOf(Props[Worker])
      context watch r // watch the routee to be able to replace them if they are terminated
      ActorRefRoutee(r) // wrap it in routee
    }

    // TODO: understand it!!
    // special Broadcast Messages will send to all of a router's routees.
    // However, do not use Broadcast Messages when you use BalancingPool for routees
    Router(RoundRobinRoutingLogic(), routees)
    Router(RandomRoutingLogic(), routees)
    Router(SmallestMailboxRoutingLogic(), routees)
    Router(BroadcastRoutingLogic(), routees)
    Router(ScatterGatherFirstCompletedRoutingLogic(10 seconds), routees)
    //    TailChoppingRoutingLogic()
    //    ConsistentHashingRoutingLogic()
  }

  override def receive = {
    case w: Work =>
      log.info("routing work from={}, current={}", sender(), self)
      router.route(w, sender())
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(Props[Worker])
      context watch r
      router = router.addRoutee(r)
  }
}

class Worker extends Actor with ActorLogging {
  override def receive = {
    case _: Work =>
      log.info("got work from: {}", sender())
  }
}


class Bootstrap extends Actor with ActorLogging {

  val master = context.actorOf(Props[Master], name = "master")

  override def receive = {
    case "Start" =>
      1 to 500 foreach { _ => master ! Work() }
  }
}

object RoutingMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val bootstrap = system.actorOf(Props[Bootstrap], name = "bootstrap")

  bootstrap ! "Start"

  implicit def inWithTimes(n: Int) = new {
    def times(f: => Unit) = 1 to n foreach { _ => f }
  }

  // WOW, bugs!!
  //  List(1 to 10).foreach(x => master ! Work())

  //  5 times {
  //    master ! Work()
  //  }

  //  master ! Work()
  //  master ! Work()

  Thread.sleep(10000)
  system.terminate()
}

case class Work()
