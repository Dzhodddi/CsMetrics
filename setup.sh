#!/bin/bash'

set -e

CONTAINER_NAME="csmetrics-db"
DB_USER="postgres"
DB_PASSWORD="postgres"
DB_NAME="metrics"
DB_PORT="5432"
MIGRATIONS_DIR="./sql"

if [ ! "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
    docker run -d \
      --name $CONTAINER_NAME \
      -e POSTGRES_USER=$DB_USER \
      -e POSTGRES_PASSWORD=$DB_PASSWORD \
      -e POSTGRES_DB=$DB_NAME \
      -p $DB_PORT:5432 \
      postgres:17.6
else
    docker start $CONTAINER_NAME
fi

until docker exec $CONTAINER_NAME pg_isready -U $DB_USER -d $DB_NAME > /dev/null 2>&1; do
  sleep 2
done

if [ ! -d "$MIGRATIONS_DIR" ] || [ -z "$(ls -A $MIGRATIONS_DIR/*.sql 2>/dev/null)" ]; then
    exit 1
fi

for file in $(ls $MIGRATIONS_DIR/*.sql | sort -V); do
    docker exec -i $CONTAINER_NAME psql -U $DB_USER -d $DB_NAME < "$file"
    if [ $? -ne 0 ]; then
        exit 1
    fi
done