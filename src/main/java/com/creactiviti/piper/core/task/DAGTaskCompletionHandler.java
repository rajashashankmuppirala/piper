package com.creactiviti.piper.core.task;

import com.creactiviti.piper.core.DSL;
import com.creactiviti.piper.core.MapObject;
import com.creactiviti.piper.core.TaskCompletionHandler;
import com.creactiviti.piper.core.context.ContextRepository;
import com.creactiviti.piper.core.context.DAGRepository;
import com.creactiviti.piper.core.uuid.UUIDGenerator;

import java.util.*;

public class DAGTaskCompletionHandler implements TaskCompletionHandler {

    private final TaskExecutionRepository taskExecutionRepo;
    private final TaskCompletionHandler taskCompletionHandler;
    private final CounterRepository counterRepository;
    private final TaskDispatcher taskDispatcher;
    private final DAGRepository dagRepository;

    public DAGTaskCompletionHandler(TaskExecutionRepository taskExecutionRepo, TaskCompletionHandler taskCompletionHandler,
                                    CounterRepository counterRepository, TaskDispatcher taskDispatcher, DAGRepository dagRepository) {
        this.taskExecutionRepo = taskExecutionRepo;
        this.taskCompletionHandler = taskCompletionHandler;
        this.counterRepository = counterRepository;
        this.taskDispatcher = taskDispatcher;
        this.dagRepository = dagRepository;
    }

    @Override
    public void handle(TaskExecution aTaskExecution) {
        SimpleTaskExecution mtask = SimpleTaskExecution.of(aTaskExecution);
        mtask.setStatus(TaskStatus.COMPLETED);
        taskExecutionRepo.merge(mtask);
        dagRepository.notifyTask(aTaskExecution.getParentId(), aTaskExecution.getName());

        long tasksLeft = counterRepository.decrement(aTaskExecution.getParentId());
        if(tasksLeft == 0) {
            taskCompletionHandler.handle(taskExecutionRepo.findOne(aTaskExecution.getParentId()));
            counterRepository.delete(aTaskExecution.getParentId());
            dagRepository.delete(aTaskExecution.getParentId());
        } else {
            MapObject nextTask = dagRepository.getNextTask(aTaskExecution.getParentId());
            if(null!= nextTask) {
                SimpleTaskExecution dagTask = SimpleTaskExecution.of(nextTask);
                dagTask.setId(UUIDGenerator.generate());
                dagTask.setParentId(aTaskExecution.getParentId());
                dagTask.setStatus(TaskStatus.CREATED);
                dagTask.setJobId(aTaskExecution.getJobId());
                dagTask.setCreateTime(new Date());
                dagTask.setPriority(aTaskExecution.getPriority());
                taskExecutionRepo.create(dagTask);
                taskDispatcher.dispatch(dagTask);

            }
        }
    }

    @Override
    public boolean canHandle(TaskExecution aTaskExecution) {
        String parentId = aTaskExecution.getParentId();
        if(parentId!=null) {
            TaskExecution parentExecution = taskExecutionRepo.findOne(parentId);
            return parentExecution.getType().equals(DSL.DAG);
        }
        return false;
    }
}
