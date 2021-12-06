package com.creactiviti.piper.core.context;

import com.creactiviti.piper.core.MapObject;
import com.google.common.collect.ArrayListMultimap;

import java.util.*;
import java.util.stream.Collectors;

public class DAGRepository {

    private final Map<String,Set<MapObject>> tasksMap = new HashMap<>();
    private final Map<String,ArrayListMultimap<MapObject,MapObject>> dependenciesMap = new HashMap<>();

    public void updateTasksAndDependency(String aStackId,MapObject task, MapObject dependencyTask){
        updateDAGTask(aStackId,task);
        updateDAGDependency(aStackId,task,dependencyTask);
    }

    public void updateTasksAndDependencies(String aStackId,MapObject task, Set<MapObject> dependencyTasks){
        updateDAGTask(aStackId,task);
        updateDAGDependency(aStackId,task,dependencyTasks);
    }

    public void updateTasks(String aStackId,MapObject task){
        updateDAGTask(aStackId,task);
    }

    public synchronized Set<MapObject> getNextTasks(String aStackId){
        Set<MapObject> nextTasks = peekNextTasks(aStackId);
        tasksMap.get(aStackId).removeAll(nextTasks);
        return nextTasks;
    }
    
    private synchronized Set<MapObject> peekNextTasks(String aStackId){
        return tasksMap.get(aStackId).stream().filter(task ->
            (dependenciesMap.get(aStackId)== null || dependenciesMap.get(aStackId).get(task).isEmpty())).collect(Collectors.toSet());
    }

    public Set<MapObject> getRootNodes(String aStackId){
        return tasksMap.get(aStackId).stream().filter(mapObject -> dependenciesMap.get(aStackId) == null || !dependenciesMap.get(aStackId).containsKey(mapObject))
                .collect(Collectors.toSet());
    }

    public void notifyTasks(String aStackId, Set<MapObject> tasks){
        tasksMap.get(aStackId).removeAll(tasks);
    }

    public synchronized void notifyTask(String aStackId, String taskName){
        if(null!= dependenciesMap.get(aStackId)){
            Set<MapObject> tasks = dependenciesMap.get(aStackId).values().stream()
                    .filter(mapObject -> mapObject.get("name").equals(taskName)).collect(Collectors.toSet());
            if(null!=tasks && !tasks.isEmpty()){
                dependenciesMap.get(aStackId).values().removeAll(tasks);
            }
        }
    }

    private void updateDAGTask(String aStackId, MapObject task){
        if (tasksMap.containsKey(aStackId)){
            tasksMap.get(aStackId).add(task);
        } else {
            tasksMap.put(aStackId, new HashSet<>(Arrays.asList(task)));
        }
    }

    private void updateDAGDependency(String aStackId, MapObject task, Object dependencyTask){
        if (dependenciesMap.containsKey(aStackId)){
            if(dependencyTask instanceof MapObject){
                dependenciesMap.get(aStackId).put(task, (MapObject) dependencyTask);
            } else if (dependencyTask instanceof Set){
                dependenciesMap.get(aStackId).putAll(task, (Set<? extends MapObject>) dependencyTask);
            }
        } else {
            ArrayListMultimap<MapObject,MapObject> dependencyMap = ArrayListMultimap.create();
            if(dependencyTask instanceof MapObject){
                dependencyMap.put(task, (MapObject) dependencyTask);
                dependenciesMap.put(aStackId, dependencyMap);
            } else if (dependencyTask instanceof Set){
                dependencyMap.putAll(task, (Iterable<? extends MapObject>) dependencyTask);
                dependenciesMap.put(aStackId, dependencyMap);
            }
        }
    }

    public void delete(String aStackId) {
        tasksMap.remove(aStackId);
        dependenciesMap.remove(aStackId);
    }
}
