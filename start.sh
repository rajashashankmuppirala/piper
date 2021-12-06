#!/bin/bash

docker run \
  --name=piper \
  --link postgres:postgres \
  --rm \
  -it \
  -e spring.datasource.url=jdbc:postgresql://postgres:5432/piper \
  -e spring.datasource.initialization-mode=always \
  -e logging.level.com.creactiviti=INFO \
  -e piper.worker.enabled=true \
  -e piper.coordinator.enabled=true \
  -e piper.worker.subscriptions.tasks=1 \
  -e piper.pipeline-repository.filesystem.enabled=true \
  -e piper.pipeline-repository.filesystem.location-pattern=/pipelines/**/*.yaml \
  -v $PWD:/pipelines \
  -p 8080:8080 \
  creactiviti/piper
