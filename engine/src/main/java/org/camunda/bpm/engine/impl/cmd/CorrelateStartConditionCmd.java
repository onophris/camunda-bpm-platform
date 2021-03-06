/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.cmd;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ConditionCorrelationBuilderImpl;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.runtime.ConditionHandler;
import org.camunda.bpm.engine.impl.runtime.ConditionHandlerResult;
import org.camunda.bpm.engine.impl.runtime.ConditionSet;
import org.camunda.bpm.engine.runtime.ProcessInstance;

public class CorrelateStartConditionCmd implements Command<List<ProcessInstance>> {

  protected ConditionCorrelationBuilderImpl builder;

  public CorrelateStartConditionCmd(ConditionCorrelationBuilderImpl builder) {
    this.builder = builder;
  }

  @Override
  public List<ProcessInstance> execute(final CommandContext commandContext) {
    ensureNotNull(BadUserRequestException.class, "Variables are mandatory to start process instance by condition", "variables", builder.getVariables());

    final ConditionHandler conditionHandler = commandContext.getProcessEngineConfiguration().getConditionHandler();
    final ConditionSet conditionSet = new ConditionSet(builder);

    List<ConditionHandlerResult> results = commandContext.runWithoutAuthorization(new Callable<List<ConditionHandlerResult>>() {
      public List<ConditionHandlerResult> call() throws Exception {
        return conditionHandler.correlateStartCondition(commandContext, conditionSet);
      }
    });

    if (results.isEmpty()) {
      throw new ProcessEngineException("No process instances were started during correlation of the conditional start events.");
    }

    for (ConditionHandlerResult ConditionHandlerResult : results) {
      checkAuthorization(commandContext, ConditionHandlerResult);
    }

    List<ProcessInstance> processInstances = new ArrayList<ProcessInstance>();
    for (ConditionHandlerResult ConditionHandlerResult : results) {
      processInstances.add(instantiateProcess(commandContext, ConditionHandlerResult));
    }

    return processInstances;
  }

  protected void checkAuthorization(CommandContext commandContext, ConditionHandlerResult result) {
    for (CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
      ProcessDefinitionEntity definition = result.getProcessDefinition();
      checker.checkCreateProcessInstance(definition);
    }
  }

  protected ProcessInstance instantiateProcess(CommandContext commandContext, ConditionHandlerResult result) {
    ProcessDefinitionEntity processDefinitionEntity = result.getProcessDefinition();

    ActivityImpl startEvent = processDefinitionEntity.findActivity(result.getActivity().getActivityId());
    ExecutionEntity processInstance = processDefinitionEntity.createProcessInstance(builder.getBusinessKey(), startEvent);
    processInstance.start(builder.getVariables());

    return processInstance;
  }

}
