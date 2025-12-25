#!/bin/bash
set -e  # Exit on error

# Check if oltpbenchmark exists
if [ ! -f "./oltpbenchmark" ]; then
    echo "Error: oltpbenchmark not found. Please build the project first."
    exit 1
fi

# Check if config file exists
if [ ! -f "tpcc_ora_build.xml" ]; then
    echo "Error: tpcc_ora_build.xml not found."
    exit 1
fi

# Check if SQL script exists
if [ ! -f "config/drop_build_oracle.sql" ]; then
    echo "Error: config/drop_build_oracle.sql not found."
    exit 1
fi

# Drop existing tables
echo "Dropping existing tables..."
./oltpbenchmark -b tpcc -c tpcc_ora_build.xml --runscript config/drop_build_oracle.sql

# Create and load data
echo "Creating tables and loading data..."
./oltpbenchmark -b tpcc -c tpcc_ora_build.xml --clear=true --create=true --load=true

echo "Build completed successfully."
