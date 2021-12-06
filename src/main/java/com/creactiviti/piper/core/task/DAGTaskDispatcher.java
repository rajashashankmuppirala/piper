package com.creactiviti.piper.core.task;

import com.creactiviti.piper.core.DSL;
import com.creactiviti.piper.core.MapObject;
import com.creactiviti.piper.core.context.ContextRepository;
import com.creactiviti.piper.core.context.DAGRepository;
import com.creactiviti.piper.core.messagebroker.MessageBroker;
import com.creactiviti.piper.core.messagebroker.Queues;
import com.creactiviti.piper.core.uuid.UUIDGenerator;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

public class DAGTaskDispatcher implements TaskDispatcher<TaskExecution>, TaskDispatcherResolver{

    private final TaskDispatcher taskDispatcher;
    private final TaskExecutionRepository taskExecutionRepo;
    private final MessageBroker messageBroker;
    private final CounterRepository counterRepository;
    private final DAGRepository dagRepository;

    public DAGTaskDispatcher(TaskDispatcher taskDispatcher, TaskExecutionRepository taskExecutionRepo, MessageBroker messageBroker, ContextRepository contextRepository, CounterRepository counterRepository, DAGRepository dagRepository) {
        this.taskDispatcher = taskDispatcher;
        this.taskExecutionRepo = taskExecutionRepo;
        this.messageBroker = messageBroker;
        this.counterRepository = counterRepository;
        this.dagRepository = dagRepository;
    }

    @Override
    public void dispatch(TaskExecution aTask) {
        List<MapObject> tasks = aTask.getList("tasks", MapObject.class);
        Assert.notNull(tasks,"'tasks' property can't be null");
        if(tasks.size() > 0) {
            counterRepository.set(aTask.getId(), tasks.size());

            updateDAG(aTask.getId(), tasks);
            Set<MapObject> rootNodes = dagRepository.getRootNodes(aTask.getId());
            for(MapObject rootTask: rootNodes){
                SimpleTaskExecution dagTask = SimpleTaskExecution.of(rootTask);
                dagTask.setId(UUIDGenerator.generate());
                dagTask.setParentId(aTask.getId());
                dagTask.setStatus(TaskStatus.CREATED);
                dagTask.setJobId(aTask.getJobId());
                dagTask.setCreateTime(new Date());
                dagTask.setPriority(aTask.getPriority());
                taskExecutionRepo.create(dagTask);
                taskDispatcher.dispatch(dagTask);
            }
            dagRepository.notifyTasks(aTask.getId(), rootNodes);

        } else {
            SimpleTaskExecution completion = SimpleTaskExecution.of(aTask);
            completion.setEndTime(new Date());
            messageBroker.send(Queues.COMPLETIONS, completion);
        }
    }

    @Override
    public TaskDispatcher resolve(Task aTask) {
        if(aTask.getType().equals(DSL.DAG)) {
            return this;
        }
        return null;
    }


    private void updateDAG(String aStackId,List<MapObject> tasks){
        for(MapObject task: tasks){
            dagRepository.updateTasks(aStackId, task);
            ArrayList<String> parentList = (ArrayList) task.get("depends");
            if(null!= parentList && !parentList.isEmpty()) {
                Set<MapObject> dependentTasks = tasks.stream()
                        .filter(mapObject -> parentList.contains(mapObject.get("name"))).collect(Collectors.toSet());

                if (null != dependentTasks && !dependentTasks.isEmpty()) {
                    if (dependentTasks.size() > 1) {
                        dagRepository.updateTasksAndDependencies(aStackId,task, dependentTasks);
                    } else {
                        dagRepository.updateTasksAndDependency(aStackId,task, dependentTasks.stream().findFirst().get());
                    }
                }
            }
        }
    }
}
