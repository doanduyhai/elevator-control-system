package com.doanduyhai.elevator.integration

import java.io.{PrintStream, ByteArrayOutputStream}

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.doanduyhai.elevator.FileContentReader
import com.doanduyhai.elevator.actors._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.Queue
import scala.concurrent.duration._


class IntegrationTest extends TestKit(ActorSystem("SimulationActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll with FileContentReader{

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ControlSystem" should "simulate simple scenario with 1 elevator moving" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(1, printStream = new PrintStream(baos))))
    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))

    //When
    elevator1 ! StartSimulation

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include(readContentFromFile("integration/simulate_simple_scenario_with_1_elevator_moving"))
  }

  "ControlSystem" should "simulate simple scenario with 2 elevators (moving and still)" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(2, printStream = new PrintStream(baos))))
    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
    val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, AtFloor(2), timeStep)))

    //When
    elevator1 ! StartSimulation
    elevator2 ! StartSimulation

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include(
      """
        |--------------------------------------------------
        |1[    ]: |0> _  _ {3}
        |2[    ]:  _  _ |2|
        |--------------------------------------------------""".stripMargin)
    display should include(
      """
        |--------------------------------------------------
        |1[    ]:  _  _  _ |3|
        |2[    ]:  _  _ |2|
        |--------------------------------------------------""".stripMargin)
  }

  "ControlSystem" should "simulate simple scenario with 2 elevators (moving and (still with scheduled order))" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()
    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(2, printStream = new PrintStream(baos))))

    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
    val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, AtFloor(2), timeStep, Some(Pickup(Move(5,3))))))

    //When
    elevator1 ! StartSimulation
    elevator2 ! StartSimulation

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())
    display should include("1[    ]: |0> _  _ {3}")
    display should include("2[5->3]:  _  _ |2|")
    display should include(
      """
        |--------------------------------------------------
        |1[    ]:  _  _  _ |3|
        |2[5->3]:  _  _  _  _  _ |5|
        |--------------------------------------------------""".stripMargin)
    display should include(
      """
        |--------------------------------------------------
        |1[    ]:  _  _  _ |3|
        |2[    ]:  _  _  _ |3|
        |--------------------------------------------------""".stripMargin)
  }

  "ControlSystem" should "simulate simple scenario with 1 elevator still and receiving a pickup order" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(1, printStream = new PrintStream(baos))))
    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(2), timeStep)))


    //When
    elevator1 ! StartSimulation
    Thread.sleep(100)

    controlSystemActor ! Pickup(Move(4,2))

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include(readContentFromFile("integration/simulate_simple_scenario_with_1_elevator_still_and_receiving_a_pickup_order"))
  }

  "ControlSystem" should "simulate simple scenario with 1 elevator moving and receiving a pickup order" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(1, printStream = new PrintStream(baos))))
    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))

    //When
    elevator1 ! StartSimulation
    Thread.sleep(20)
    controlSystemActor ! Pickup(Move(4,2))

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include("Order received: Pickup(Move(4,2))")
    display should include(
      """
        |--------------------------------------------------
        |1[4->2]:  _  _  _ |3|
        |--------------------------------------------------""".stripMargin)

    display should include(
      """
        |--------------------------------------------------
        |1[4->2]:  _  _  _  _ |4|
        |--------------------------------------------------""".stripMargin)

    display should include(
      """
        |--------------------------------------------------
        |1[    ]:  _  _ |2|
        |--------------------------------------------------""".stripMargin)
  }

  "ControlSystem" should "simulate simple scenario with 1 elevator still with no scheduled order and dequeue an order" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(1, Queue(Pickup(Move(1,2))), printStream = new PrintStream(baos))))
    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(0), timeStep)))

    //When
    elevator1 ! StartSimulation

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include(readContentFromFile("integration/simulate_simple_scenario_with_1_elevator_still_with_no_scheduled_order_and_dequeue_an_order"))
  }

  "ControlSystem" should "simulate simple scenario with 2 elevators still and moving and dequeue an order" in {
    //Given
    val timeStep = 10.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(2, Queue(Pickup(Move(1,2))), printStream = new PrintStream(baos))))

    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(0), timeStep)))
    val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, Move(3,5), timeStep)))

    //When
    elevator1 ! StartSimulation
    elevator2 ! StartSimulation

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include("Control system orders queue: [Move(1,2)]")
    display should include("Send queued pickup order: Move(1,2) to elevator 1")
    display should include(
      """
        |--------------------------------------------------
        |1[    ]: |0|
        |2[    ]:  _  _  _ |3> _ {5}
        |--------------------------------------------------""".stripMargin)

    display should include(
      """
        |1[1->2]:  _ |1|""".stripMargin)

    display should include(
      """
        |--------------------------------------------------
        |1[    ]:  _  _ |2|
        |2[    ]:  _  _  _  _  _ |5|
        |--------------------------------------------------""".stripMargin)
  }

  "ControlSystem" should "simulate  2 elevators moving with scheduled orders and enqueue then dequeue an order" in {
    //Given
    val timeStep = 20.millisecond
    val baos = new ByteArrayOutputStream()

    val controlSystemActor = system.actorOf(Props(new ControlSystemActor(2, printStream = new PrintStream(baos))))

    val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0,2), timeStep, Some(Pickup(Move(4,5))))))
    val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, Move(3,7), timeStep, Some(Pickup(Move(6,3))))))


    //When
    elevator1 ! StartSimulation
    elevator2 ! StartSimulation
    Thread.sleep(20)
    controlSystemActor ! Pickup(Move(2, 0))

    //Then
    Thread.sleep(1000)
    val display = new String(baos.toByteArray())

    display should include(
    """
      |--------------------------------------------------
      |1[4->5]: |0> _ {2}
      |2[6->3]:  _  _  _ |3> _  _  _ {7}
      |--------------------------------------------------""".stripMargin)

    display should include("Order received: Pickup(Move(2,0))")
    display should include("Control system orders queue: [Move(2,0)].")

    display should include("1[4->5]:  _  _  _  _ |4|")
    display should include("2[6->3]:  _  _  _  _  _  _  _ |7|")

    display should include("Send queued pickup order: Move(2,0) to elevator 1")

    display should include("1[2->0]:  _  _  _  _  _ |5|")
    display should include("2[    ]:  _  _  _ {3} _  _ <6|")

    display should include("1[2->0]:  _  _ |2|")
    display should include("2[    ]:  _  _  _ |3|")
    display should include(
      """
        |--------------------------------------------------
        |1[    ]: |0|
        |2[    ]:  _  _  _ |3|
        |--------------------------------------------------""".stripMargin)
  }

}
