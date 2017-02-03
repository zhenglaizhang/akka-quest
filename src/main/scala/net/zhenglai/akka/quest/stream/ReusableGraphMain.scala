package net.zhenglai.akka.quest.stream

import scala.collection.immutable

import akka.stream.FanInShape.{ Init, Name }
import akka.stream.{ FanInShape, Inlet, Outlet, Shape }

//  build reusable, encapsulated components of arbitrary input and output ports using the graph DSL.


/*
 we will build a graph junction that represents a pool of workers, where a worker is expressed as a Flow[I,O,_], i.e. a simple transformation of jobs of type I to results of type O (as you have seen already, this flow can actually contain a complex graph inside). Our reusable worker pool junction will not preserve the order of the incoming jobs (they are assumed to have a proper ID field) and it will use a Balance junction to schedule jobs to available workers. On top of this, our junction will feature a "fastlane", a dedicated port where jobs of higher priority can be sent.
 */
case class PriorityWorkerPoolShape[In, Out](
  jobsIn: Inlet[In],
  priorityJobsIn: Inlet[In],
  resultsOut: Outlet[Out]
) extends Shape {

  // It is important to provide the list of all input and output
  // ports with a stable order. Duplicates are not allowed.
  override val inlets: immutable.Seq[Inlet[_]] = jobsIn :: priorityJobsIn :: Nil

  override val outlets: immutable.Seq[Outlet[_]] = resultsOut :: Nil

  // A Shape must be able to create a copy of itself. Basically
  // it means a new instance with copies of the ports
  override def deepCopy() = PriorityWorkerPoolShape(
    jobsIn.carbonCopy(),
    priorityJobsIn.carbonCopy(),
    resultsOut.carbonCopy()
  )

  override def copyFromPorts(
    inlets: immutable.Seq[Inlet[_]],
    outlets: immutable.Seq[Outlet[_]]
  ) = {
    assert(inlets.size == this.inlets.size)
    assert(outlets.size == this.outlets.size)

    // This is why order matters when overriding inlets and outlets
    PriorityWorkerPoolShape[In, Out](inlets(0).as[In], inlets(1).as[In], outlets(0).as[Out])
  }
}

// Predefined shapes
// In general a custom Shape needs to be able to provide all its input and output ports, be able to copy itself, and also be able to create a new instance from given ports
// 1) `SourceShape`, `SinkShape`, `FlowShape` for simpler shapes
// 2) UniformFanInShape and UniformFanOutShape for junctions with multiple input (or output) ports of the same type,
// 3) FanInShape1, FanInShape2, ..., FanOutShape1, FanOutShape2, ... for junctions with multiple input (or output) ports of different types.


// Since our shape has two input ports and one output port, we can just use the FanInShape DSL to define our custom shape:
class PriorityWorkerPoolShape2[In, Out](_init: Init[Out] = Name("PriorityWorkerPool"))
  extends FanInShape[Out](_init) {
  protected def construct(init: Init[Out]) = new PriorityWorkerPoolShape2(i)

  val jobsIn = newInlet[In]("jobsIn")
  val priorityJobsIn = newInlet[In]("priorityJobsIn")

  // Outlet[Out] with name "out" is automatically created
}


object ReusableGraphMain extends App {

}
