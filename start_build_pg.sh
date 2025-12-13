./oltpbenchmark -b tpcc -c tpcc_postgres_build.xml --runscript config/drop_build_pg.sql
./oltpbenchmark -b tpcc -c tpcc_postgres_build.xml --clear=true --create=true --load=true 
