/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.tensorflow.api.learn.layers.rnn

import org.platanios.tensorflow.api.core.types.TF
import org.platanios.tensorflow.api.implicits.helpers.{NestedStructure, Zero}
import org.platanios.tensorflow.api.learn.Mode
import org.platanios.tensorflow.api.learn.layers.Layer
import org.platanios.tensorflow.api.learn.layers.rnn.cell.{RNNCell, Tuple}
import org.platanios.tensorflow.api.ops
import org.platanios.tensorflow.api.ops.Basic
import org.platanios.tensorflow.api.tensors.Tensor

/** Creates a bidirectional dynamic RNN layer.
  *
  * $OpDocRNNBidirectionalDynamicRNN
  *
  * @param  name               Name scope (also acting as variable scope) for this layer.
  * @param  cellFw             RNN cell to use for the forward direction.
  * @param  cellBw             RNN cell to use for the backward direction.
  * @param  initialStateFw     Initial state to use for the forward RNN, which is a structure over tensors with shapes
  *                            `[batchSize, stateShape(i)(0), stateShape(i)(1), ...]`, where `i` corresponds to the
  *                            index of the corresponding state. Defaults to a zero state.
  * @param  initialStateBw     Initial state to use for the backward RNN, which is a structure over tensors with shapes
  *                            `[batchSize, stateShape(i)(0), stateShape(i)(1), ...]`, where `i` corresponds to the
  *                            index of the corresponding state. Defaults to a zero state.
  * @param  timeMajor          Boolean value indicating whether the inputs are provided in time-major format (i.e.,
  *                            have shape `[time, batch, depth]`) or in batch-major format (i.e., have shape
  *                            `[batch, time, depth]`).
  * @param  parallelIterations Number of RNN loop iterations allowed to run in parallel.
  * @param  swapMemory         If `true`, GPU-CPU memory swapping support is enabled for the RNN loop.
  * @param  sequenceLengths    Optional tensor with shape `[batchSize]` containing the sequence lengths for
  *                            each row in the batch.
  *
  * @author Emmanouil Antonios Platanios
  */
class BidirectionalRNN[O, S](
    override val name: String,
    val cellFw: RNNCell[O, S],
    val cellBw: RNNCell[O, S],
    val initialStateFw: () => S = null,
    val initialStateBw: () => S = null,
    val timeMajor: Boolean = false,
    val parallelIterations: Int = 32,
    val swapMemory: Boolean = false,
    val sequenceLengths: Tensor[Int] = null
)(implicit
    protected val evStructureO: NestedStructure[O],
    protected val evStructureS: NestedStructure[S],
    protected val evZeroO: Zero[O],
    protected val evZeroS: Zero[S]
) extends Layer[O, (Tuple[O, S], Tuple[O, S])](name) {
  protected implicit val evStructureOAux: NestedStructure.Aux[O, evStructureO.V, evStructureO.D, evStructureO.S] = {
    evStructureO.asAux()
  }

  protected implicit val evStructureSAux: NestedStructure.Aux[S, evStructureS.V, evStructureS.D, evStructureS.S] = {
    evStructureS.asAux()
  }

  protected implicit val evZeroOAux: Zero.Aux[O, evStructureO.S] = {
    evZeroO.asInstanceOf[Zero.Aux[O, evStructureO.S]]
  }

  protected implicit val evZeroSAux: Zero.Aux[S, evStructureS.S] = {
    evZeroS.asInstanceOf[Zero.Aux[S, evStructureS.S]]
  }

  override val layerType: String = "BidirectionalRNN"

  override def forwardWithoutContext(input: O)(implicit mode: Mode): (Tuple[O, S], Tuple[O, S]) = {
    val stateFw = if (initialStateFw == null) None else Some(initialStateFw())
    val stateBw = if (initialStateBw == null) None else Some(initialStateBw())
    val lengths = if (sequenceLengths == null) null else ops.Basic.constant(sequenceLengths)
    val inputShape = evStructureO.shapeFromOutput(input)
    val createdCellFw = cellFw.createCell(mode, inputShape)
    val createdCellBw = cellBw.createCell(mode, inputShape)
    ops.rnn.RNN.bidirectionalDynamicRNN(
      createdCellFw, createdCellBw, input, stateFw, stateBw,
      timeMajor, parallelIterations, swapMemory, lengths, name)
  }

  def withConcatenatedOutputs: Layer[O, Tuple[O, (S, S)]] = {
    new Layer[O, Tuple[O, (S, S)]](s"$name/ConcatenatedOutputs") {
      override val layerType: String = "BidirectionalRNNWithConcatenatedOutputs"

      override def forwardWithoutContext(input: O)(implicit mode: Mode): Tuple[O, (S, S)] = {
        val raw = BidirectionalRNN.this (input)
        val output = evStructureO.decodeOutputFromOutput(
          raw._1.output, evStructureO.outputs(raw._1.output).zip(evStructureO.outputs(raw._2.output)).map(o => {
            Basic.concatenate(Seq(o._1, o._2), -1)(TF.fromDataType(o._1.dataType))
          }))._1
        Tuple(output, (raw._1.state, raw._2.state))
      }
    }
  }
}

object BidirectionalRNN {
  def apply[O, S](
      variableScope: String,
      cellFw: RNNCell[O, S],
      cellBw: RNNCell[O, S],
      initialStateFw: () => S = null,
      initialStateBw: () => S = null,
      timeMajor: Boolean = false,
      parallelIterations: Int = 32,
      swapMemory: Boolean = false,
      sequenceLengths: Tensor[Int] = null
  )(implicit
      evStructureO: NestedStructure[O],
      evStructureS: NestedStructure[S],
      evZeroO: Zero[O],
      evZeroS: Zero[S]
  ): BidirectionalRNN[O, S] = {
    new BidirectionalRNN(
      variableScope, cellFw, cellBw, initialStateFw, initialStateBw,
      timeMajor, parallelIterations, swapMemory, sequenceLengths)
  }
}
