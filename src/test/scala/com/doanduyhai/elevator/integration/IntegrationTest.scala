package com.doanduyhai.elevator.integration

import java.io.{PrintStream, ByteArrayOutputStream}

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.doanduyhai.elevator.ExpectedOutputParser
import com.doanduyhai.elevator.actors._
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.Queue
import scala.concurrent.duration._


class IntegrationTest extends TestKit(ActorSystem("SimulationActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with ExpectedOutputParser{

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "SimulationActor" must {

    "simulate simple scenario with 1 elevator moving" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
      val map = Map(1 -> (elevator1, Move(0, 3), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())
      display shouldBe readContentFromFile("integration/simulate_simple_scenario_with_1_elevator_moving")
    }

    "simulate simple scenario with 2 elevators moving and still" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
      val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, AtFloor(2), timeStep)))
      val map = Map(1 -> (elevator1, Move(0, 3), None), 2 -> (elevator2, AtFloor(2), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation

      //Then
      root.expectMsg(SimulationFinished)
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

    "simulate simple scenario with 2 elevators moving and still with scheduled order" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
      val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, AtFloor(2), timeStep, Some(Pickup(Move(5,3))))))
      val map = Map(1 -> (elevator1, Move(0, 3), None), 2 -> (elevator2, AtFloor(2), Some(Pickup(Move(5,3)))))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())
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

    "simulate simple scenario with 1 elevator still and receiving a pickup order" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(2), timeStep)))
      val map = Map(1 -> (elevator1, AtFloor(2), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation
      simulationActor ! Pickup(Move(4,2))

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())

      display should include(
        """
          |    Order received: Pickup(Move(4,2))
          |
          |--------------------------------------------------
          |1[    ]:  _  _ |2|
          |--------------------------------------------------""".stripMargin)
      display should include(
        """
          |--------------------------------------------------
          |1[4->2]:  _  _ |2> _ {4}
          |--------------------------------------------------""".stripMargin)
      display should include(
        """
          |--------------------------------------------------
          |1[4->2]:  _  _  _  _ |4|
          |--------------------------------------------------""".stripMargin)
      display should include(
        """
          |--------------------------------------------------
          |1[    ]:  _  _ {2}<3|
          |--------------------------------------------------""".stripMargin)
    }

    "simulate simple scenario with 1 elevator moving and receiving a pickup order" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0, 3), timeStep)))
      val map = Map(1 -> (elevator1, Move(0, 3), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation
      simulationActor ! Pickup(Move(4,2))

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())

      display should include(
        """
          |    Order received: Pickup(Move(4,2))
          |
          |--------------------------------------------------
          |1[    ]: |0> _  _ {3}
          |--------------------------------------------------""".stripMargin)
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

    "simulate simple scenario with 1 elevator still with no scheduled order and dequeue an order" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(0), timeStep)))
      val map = Map(1 -> (elevator1, AtFloor(0), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor, Queue(Pickup(Move(1,2))), 5.millisecond)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())

      display should include("Control system orders queue: [Move(1,2)]")
      display should include(
        """
          |--------------------------------------------------
          |1[    ]: |0|
          |--------------------------------------------------""".stripMargin)

      display should include(
        """
          |--------------------------------------------------
          |1[1->2]:  _ |1|
          |--------------------------------------------------""".stripMargin)

      display should include(
        """
          |--------------------------------------------------
          |1[    ]:  _  _ |2|
          |--------------------------------------------------""".stripMargin)
    }

    "simulate simple scenario with 2 elevators still + moving and dequeue an order" in {
      //Given
      val timeStep = 10.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, AtFloor(0), timeStep)))
      val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, Move(3,5), timeStep)))
      val map = Map(1 -> (elevator1, AtFloor(0), None), 2 -> (elevator2, Move(3,5), None))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor, Queue(Pickup(Move(1,2))), 5.millisecond)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())

      display should include("Control system orders queue: [Move(1,2)]")
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

    "simulate  2 elevators moving with scheduled orders and enqueue then dequeue an order" in {
      //Given
      val timeStep = 20.millisecond
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      var simulationActor:ActorRef = null
      var controlSystemActor:ActorRef = null

      val elevator1 = system.actorOf(Props(new ElevatorActor(1, controlSystemActor, Move(0,2), timeStep, Some(Pickup(Move(4,5))))))
      val elevator2 = system.actorOf(Props(new ElevatorActor(2, controlSystemActor, Move(3,7), timeStep, Some(Pickup(Move(6,3))))))
      val map = Map(1 -> (elevator1, Move(0,2), Some(Pickup(Move(4,5)))), 2 -> (elevator2, Move(3,7), Some(Pickup(Move(6,3)))))
      controlSystemActor = system.actorOf(Props(new ControlSystemActor(map, simulationActor, Queue.empty, 2.millisecond)))
      simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystemActor, timeStep, new PrintStream(baos))))

      //When
      simulationActor ! StartSimulation
      simulationActor ! Pickup(Move(2, 0))

      //Then
      root.expectMsg(SimulationFinished)
      val display = new String(baos.toByteArray())

      display should include("Order received: Pickup(Move(2,0))")
      display should include(
        """
          |--------------------------------------------------
          |1[4->5]: |0> _ {2}
          |2[6->3]:  _  _  _ |3> _  _  _ {7}
          |--------------------------------------------------""".stripMargin)
      display should include("1[4->5]:  _  _  _  _ |4|")
      display should include("2[6->3]:  _  _  _  _  _  _  _ |7|")
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
}
