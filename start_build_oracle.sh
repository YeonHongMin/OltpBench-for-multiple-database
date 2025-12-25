./oltpbenchmark -b tpcc -c tpcc_ora_build.xml --runscript config/drop_build_oracle.sql
./oltpbenchmark -b tpcc -c tpcc_ora_build.xml --clear=true --create=true --load=true 
