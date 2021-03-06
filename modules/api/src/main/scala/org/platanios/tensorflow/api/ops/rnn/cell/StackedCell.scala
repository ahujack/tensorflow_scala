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

package org.platanios.tensorflow.api.ops.rnn.cell

import org.platanios.tensorflow.api.implicits.helpers.NestedStructure
import org.platanios.tensorflow.api.ops.Op

/** RNN cell that is composed by applying a sequence of RNN cells in order.
  *
  * This means that the output of each RNN is fed to the next one as input, while the states remain separate.
  *
  * Note that this class does no variable management at all. Variable sharing should be handled based on the RNN cells
  * the caller provides to this class. The learn API provides a layer version of this class that also does some
  * management of the variables involved.
  *
  * @param  cells Cells being stacked together.
  * @param  name  Name prefix used for all new ops.
  *
  * @author Emmanouil Antonios Platanios
  */
class StackedCell[O, S] protected (
    val cells: Seq[RNNCell[O, S]],
    val name: String = "StackedCell"
)(implicit evStructureS: NestedStructure[S]) extends RNNCell[O, Seq[S]] {
  override def outputShape[OV, OD, OS](implicit evO: NestedStructure.Aux[O, OV, OD, OS]): OS = {
    cells.last.outputShape
  }

  override def stateShape[SV, SD, SS](implicit evS: NestedStructure.Aux[Seq[S], SV, SD, SS]): SS = {
    cells.map(_.stateShape(evStructureS.asAux)).asInstanceOf[SS]
  }

  override def forward[OV, OD, OS, SV, SD, SS](
      input: Tuple[O, Seq[S]]
  )(implicit
      evStructureO: NestedStructure.Aux[O, OV, OD, OS],
      evStructureSeqS: NestedStructure.Aux[Seq[S], SV, SD, SS]
  ): Tuple[O, Seq[S]] = {
    Op.nameScope(name) {
      var currentInput = input.output
      val state = cells.zip(input.state).map {
        case (cell, s) =>
          val nextTuple = cell(Tuple(currentInput, s))(evStructureO, evStructureS.asAux)
          currentInput = nextTuple.output
          nextTuple.state
      }
      Tuple(currentInput, state)
    }
  }
}

object StackedCell {
  def apply[O, S](
      cells: Seq[RNNCell[O, S]],
      name: String = "StackedCell"
  )(implicit
      evStructureS: NestedStructure[S]
  ): StackedCell[O, S] = {
    new StackedCell(cells, name)
  }
}
