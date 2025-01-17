/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.creactiviti.piper.config;

import java.util.Arrays;
import java.util.List;

import com.creactiviti.piper.core.context.DAGRepository;
import com.creactiviti.piper.core.task.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import com.creactiviti.piper.core.Coordinator;
import com.creactiviti.piper.core.DefaultJobExecutor;
import com.creactiviti.piper.core.DefaultTaskCompletionHandler;
import com.creactiviti.piper.core.EachTaskCompletionHandler;
import com.creactiviti.piper.core.ForkTaskCompletionHandler;
import com.creactiviti.piper.core.MapTaskCompletionHandler;
import com.creactiviti.piper.core.SwitchTaskCompletionHandler;
import com.creactiviti.piper.core.TaskCompletionHandler;
import com.creactiviti.piper.core.TaskCompletionHandlerChain;
import com.creactiviti.piper.core.annotations.ConditionalOnCoordinator;
import com.creactiviti.piper.core.context.ContextRepository;
import com.creactiviti.piper.core.error.ErrorHandler;
import com.creactiviti.piper.core.error.ErrorHandlerChain;
import com.creactiviti.piper.core.error.TaskExecutionErrorHandler;
import com.creactiviti.piper.core.event.EventPublisher;
import com.creactiviti.piper.core.event.JobStatusWebhookEventListener;
import com.creactiviti.piper.core.event.TaskProgressedEventListener;
import com.creactiviti.piper.core.event.TaskStartedEventListener;
import com.creactiviti.piper.core.event.TaskStartedWebhookEventListener;
import com.creactiviti.piper.core.job.JobRepository;
import com.creactiviti.piper.core.messagebroker.MessageBroker;
import com.creactiviti.piper.core.pipeline.PipelineRepository;

@Configuration
@ConditionalOnCoordinator
public class CoordinatorConfiguration {

  @Autowired private JobRepository jobRepository;
  @Autowired private TaskExecutionRepository taskExecutionRepo;
  @Autowired private ContextRepository contextRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private CounterRepository counterRepository;
  @Autowired @Lazy private MessageBroker messageBroker;
  @Autowired @Lazy private EventPublisher eventPublisher;
  @Autowired private Environment environment;

  @Bean
  Coordinator coordinator () {
    Coordinator coordinator = new Coordinator();
    coordinator.setContextRepository(contextRepository);
    coordinator.setEventPublisher(eventPublisher);
    coordinator.setJobRepository(jobRepository);
    coordinator.setJobTaskRepository(taskExecutionRepo);
    coordinator.setPipelineRepository(pipelineRepository);
    coordinator.setJobExecutor(jobExecutor());
    coordinator.setTaskDispatcher(taskDispatcher());
    coordinator.setErrorHandler(errorHandler());
    coordinator.setTaskCompletionHandler(taskCompletionHandler());
    coordinator.setMessageBroker(messageBroker);
    return coordinator;
  }

  @Bean
  DAGRepository dagRepository () {
    return new DAGRepository();
  }

  @Bean
  ErrorHandler errorHandler () {
    return new ErrorHandlerChain(Arrays.asList(jobTaskErrorHandler()));
  }

  @Bean
  TaskExecutionErrorHandler jobTaskErrorHandler () {
    TaskExecutionErrorHandler jobTaskErrorHandler = new TaskExecutionErrorHandler();
    jobTaskErrorHandler.setJobRepository(jobRepository);
    jobTaskErrorHandler.setJobTaskRepository(taskExecutionRepo);
    jobTaskErrorHandler.setTaskDispatcher(taskDispatcher());
    jobTaskErrorHandler.setEventPublisher(eventPublisher);
    return jobTaskErrorHandler;
  }

  @Bean
  TaskCompletionHandlerChain taskCompletionHandler () {
    TaskCompletionHandlerChain taskCompletionHandlerChain = new TaskCompletionHandlerChain();
    taskCompletionHandlerChain.setTaskCompletionHandlers(Arrays.asList(
      eachTaskCompletionHandler(taskCompletionHandlerChain),
      mapTaskCompletionHandler(taskCompletionHandlerChain),
      parallelTaskCompletionHandler(taskCompletionHandlerChain),
      dagTaskCompletionHandler(taskCompletionHandlerChain),
      forkTaskCompletionHandler(taskCompletionHandlerChain),
      switchTaskCompletionHandler(taskCompletionHandlerChain),
      defaultTaskCompletionHandler()
    ));
    return taskCompletionHandlerChain;
  }

  @Bean
  DefaultTaskCompletionHandler defaultTaskCompletionHandler () {
    DefaultTaskCompletionHandler taskCompletionHandler = new DefaultTaskCompletionHandler();
    taskCompletionHandler.setContextRepository(contextRepository);
    taskCompletionHandler.setJobExecutor(jobExecutor());
    taskCompletionHandler.setJobRepository(jobRepository);
    taskCompletionHandler.setJobTaskRepository(taskExecutionRepo);
    taskCompletionHandler.setPipelineRepository(pipelineRepository);
    taskCompletionHandler.setEventPublisher(eventPublisher);
    taskCompletionHandler.setTaskEvaluator(SpelTaskEvaluator.builder().environment(environment).build());
    return taskCompletionHandler;
  }

  @Bean
  SwitchTaskCompletionHandler switchTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    return new SwitchTaskCompletionHandler(
      taskExecutionRepo,
      aTaskCompletionHandler,
      taskDispatcher(),
      contextRepository,
      SpelTaskEvaluator.builder().environment(environment).build()
    );
  }

  @Bean
  SwitchTaskDispatcher switchTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    return new SwitchTaskDispatcher(
      aTaskDispatcher,
      taskExecutionRepo,
      messageBroker,
      contextRepository,
      SpelTaskEvaluator.builder().environment(environment).build()
    );
  }

  @Bean
  EachTaskCompletionHandler eachTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    return new EachTaskCompletionHandler(taskExecutionRepo,aTaskCompletionHandler,counterRepository);
  }

  @Bean
  MapTaskCompletionHandler mapTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    return new MapTaskCompletionHandler(taskExecutionRepo,aTaskCompletionHandler,counterRepository);
  }

  @Bean
  ParallelTaskCompletionHandler parallelTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    ParallelTaskCompletionHandler dispatcher = new ParallelTaskCompletionHandler();
    dispatcher.setCounterRepository(counterRepository);
    dispatcher.setTaskCompletionHandler(aTaskCompletionHandler);
    dispatcher.setTaskExecutionRepository(taskExecutionRepo);
    return dispatcher;
  }

  @Bean
  ForkTaskCompletionHandler forkTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    return new ForkTaskCompletionHandler(
      taskExecutionRepo,
      aTaskCompletionHandler,
      counterRepository,
      taskDispatcher(),
      contextRepository,
      SpelTaskEvaluator.builder().environment(environment).build()
    );
  }

  @Bean
  DAGTaskCompletionHandler dagTaskCompletionHandler (TaskCompletionHandler aTaskCompletionHandler) {
    return new DAGTaskCompletionHandler(
            taskExecutionRepo,
            aTaskCompletionHandler,
            counterRepository,
            taskDispatcher(),
            dagRepository());
  }

  @Bean
  SubflowTaskDispatcher subflowTaskDispatcher () {
    return new SubflowTaskDispatcher(messageBroker);
  }

  @Bean
  SubflowJobStatusEventListener subflowJobStatusEventListener () {
    return new SubflowJobStatusEventListener(
      jobRepository,
      taskExecutionRepo,
      coordinator(),
      SpelTaskEvaluator.builder().environment(environment).build()
    );
  }

  @Bean
  DefaultJobExecutor jobExecutor () {
    DefaultJobExecutor jobExecutor = new DefaultJobExecutor();
    jobExecutor.setContextRepository(contextRepository);
    jobExecutor.setJobTaskRepository(taskExecutionRepo);
    jobExecutor.setPipelineRepository(pipelineRepository);
    jobExecutor.setTaskDispatcher(taskDispatcher());
    jobExecutor.setTaskEvaluator(SpelTaskEvaluator.builder()
                                                  .environment(environment)
                                                  .build());
    return jobExecutor;
  }

  @Bean
  TaskDispatcherChain taskDispatcher () {
    TaskDispatcherChain taskDispatcher = new TaskDispatcherChain();
    List<TaskDispatcherResolver> resolvers =  Arrays.asList(
      eachTaskDispatcher(taskDispatcher),
      mapTaskDispatcher(taskDispatcher),
      parallelTaskDispatcher(taskDispatcher),
      dagTaskDispatcher(taskDispatcher),
      forkTaskDispatcher(taskDispatcher),
      switchTaskDispatcher(taskDispatcher),
      controlTaskDispatcher(),
      subflowTaskDispatcher(),
      workTaskDispatcher()
    );
    taskDispatcher.setResolvers(resolvers);
    return taskDispatcher;
  }

  @Bean
  ControlTaskDispatcher controlTaskDispatcher () {
    return new ControlTaskDispatcher(messageBroker);
  }

  @Bean
  EachTaskDispatcher eachTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    return new EachTaskDispatcher(
      aTaskDispatcher,
      taskExecutionRepo,
      messageBroker,
      contextRepository,
      counterRepository,
      SpelTaskEvaluator.builder().environment(environment).build()
    );
  }

  @Bean
  MapTaskDispatcher mapTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    return MapTaskDispatcher.builder()
                            .taskDispatcher(aTaskDispatcher)
                            .taskExecutionRepository(taskExecutionRepo)
                            .messageBroker(messageBroker)
                            .contextRepository(contextRepository)
                            .counterRepository(counterRepository)
                            .taskEvaluator(SpelTaskEvaluator.builder().environment(environment).build())
                            .build();
  }

  @Bean
  ParallelTaskDispatcher parallelTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    ParallelTaskDispatcher dispatcher = new ParallelTaskDispatcher();
    dispatcher.setContextRepository(contextRepository);
    dispatcher.setCounterRepository(counterRepository);
    dispatcher.setMessageBroker(messageBroker);
    dispatcher.setTaskDispatcher(aTaskDispatcher);
    dispatcher.setTaskExecutionRepository(taskExecutionRepo);
    return dispatcher;
  }

  @Bean
  ForkTaskDispatcher forkTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    ForkTaskDispatcher forkTaskDispatcher = new ForkTaskDispatcher();
    forkTaskDispatcher.setTaskDispatcher(aTaskDispatcher);
    forkTaskDispatcher.setTaskEvaluator(SpelTaskEvaluator.builder().environment(environment).build());
    forkTaskDispatcher.setTaskExecutionRepo(taskExecutionRepo);
    forkTaskDispatcher.setMessageBroker(messageBroker);
    forkTaskDispatcher.setContextRepository(contextRepository);
    forkTaskDispatcher.setCounterRepository(counterRepository);
    return forkTaskDispatcher;
  }

  @Bean
  WorkTaskDispatcher workTaskDispatcher () {
    return new WorkTaskDispatcher(messageBroker);
  }

  @Bean
  DAGTaskDispatcher dagTaskDispatcher (TaskDispatcher aTaskDispatcher) {
    return new DAGTaskDispatcher(
            aTaskDispatcher,
            taskExecutionRepo,
            messageBroker,
            contextRepository,
            counterRepository,
            dagRepository()
    );
  }

  @Bean
  TaskStartedEventListener taskStartedEventListener () {
    return new TaskStartedEventListener(taskExecutionRepo, taskDispatcher(), jobRepository);
  }

  @Bean
  TaskProgressedEventListener taskProgressedEventListener () {
    return new TaskProgressedEventListener(taskExecutionRepo);
  }

  @Bean
  JobStatusWebhookEventListener webhookEventHandler () {
    return new JobStatusWebhookEventListener(jobRepository);
  }

  @Bean
  TaskStartedWebhookEventListener taskStartedWebhookEventListener () {
    return new TaskStartedWebhookEventListener(jobRepository);
  }

}
