./oltpbenchmark -b tpcc -c tpcc_tibero_build.xml --runscript config/drop_build_tibero.sql
./oltpbenchmark -b tpcc -c tpcc_tibero_build.xml --clear=true --create=true --load=true 
