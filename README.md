## Objetivo

En esta guiá veremos como conectar la base de datos en cache “*Redis*” con el popular framework de desarrollo para el lenguaje java “*Spring*”, elaboraremos paso a paso un pequeño proyecto de ejemplo utilizando diversas tecnologías como docker, docker-compose, redis, java, spring y mysql.



En este pequeño proyecto realizaremos una aplicación java-spring que nos permitirá registrar usuarios en una base de datos mysql y al consultarlos almacenar los usuarios en redis para agilizar las próximas consultas de aquellos usuarios ya consultados, crearemos la tabla a través de migraciones con flyway y deployaremos nuestra aplicación junto a mysql y redis en contenedores utilizando docker y docker-compose.



## Tecnologías a usar



Antes de empezar con nuestra guiá se mostrarán una definición de cada tecnología que utilizaremos en este pequeño proyecto.



**Redis**: es un almacén de estructura de datos de valores de clave en memoria rápido y de código abierto.



**MySQL**:es el sistema de gestión de bases de datos relacional más extendido en la actualidad al estar basada en código abierto.



**Docker**: es la tecnología de organización en contenedores que posibilita la creación y el uso de los contenedores de linux.



**Docker Compose**: es una herramienta para definir y ejecutar aplicaciones de Docker de varios contenedores utilizando archivos YML.



**Java**: es un lenguaje de programación ampliamente utilizado para codificar aplicaciones



**Spring Framework**: es un framework de código abierto que da soporte para el desarrollo de aplicaciones y páginas webs basadas en Java. Se trata de uno de los entornos más populares y ayuda a los desarrolladores back-end a crear aplicaciones con un alto rendimiento empleando objetos de java sencillos.





## ¿Porque usar Redis?



Como se menciono anteriormente, redis es una base de datos que se encuentra en memoria lo cual permite un rápido acceso a la información ahí almacenada, es simple y fácil de usar ya que nos permite guardar y extraer información de forma sencilla y flexible por la gran cantidad de tipos de datos que esta pone a nuestra disposición además de ser de código abierto con una enorme comunidad muy activa a su alrededor.



Los aplicativos de redis son variados, estos van desde guardados de sesiones y configuraciones en aplicaciones web, desde salas de chats incluso streaming, sin embargo, el uso más frecuente que esta base de datos suele tener es para el almacenamiento y extracción de información muy constantemente solicitada disminuyendo la latencia de las peticiones aliviando la carga de servidores y gestores de base de datos relacionales y no relacionales.



A pesar de existir algunas alternativas a redis como por ejemplo memcached, esta tiene algunas desventajas ante redis.  Memcached nos permite almacenar solo string mientras que redis tiene una amplia variedad de estructuras de datos que nos ofrece para su utilización, memcached solo permite almacenar hasta 1 mb por cada dato, redis hasta 512 mb.



## Setup inicial

Crearemos nuestro proyecto spring el cual llamaremos spring-redis, utilizaremos la versión java 17 que es la que se encuentra instalada en mi equipo, gradle como gestor de dependencias y el empaquetado sera un jar.



Una vez creado nuestro proyecto procederemos a incorporar las siguientes dependencias en el archivo build.gradle:



Las dos siguientes dependencias nos ayudaran a interactuar con redis en nuestra aplicacion
```
implementation("redis.clients:jedis:5.1.0")
implementation("org.springframework.data:spring-data-redis:3.2.2")
```

Esta dependencia nos permitira realizar las migración de base de datos a través de la librería flyway
```
implementation("org.flywaydb:flyway-core:10.8.1")
implementation("org.flywaydb:flyway-mysql:10.8.1")
```

dentro de nuestro build.gradle incorporamos el plugin de flyway
```
id("org.flywaydb.flyway") version "10.8.1"
```


agregamos también el task al final del mismo archivo
```
flyway {
    url = "jdbc:mysql://mysql/pruebas" 
    user = "root" 
    password = "1234567890" 
    locations = ["classpath:db/"] 
}
```

Una vez incorporadas procederemos a buildear nuestro gradle para que las dependencias sean descargadas e incorporadas a nuestro proyecto para su utilizacion.



## Configuracion de properties

Incorporamos las propiedades necesarias para las conexiones de redis, mysql y la configuración de flyway en el archivo application.properties
```
server.port=8080

schema.database=pruebas
url.database=jdbc:mysql://mysql/${schema.database}
user.database=root
password.database=1234567890

redis.host=redis
redis.port=6379

spring.datasource.url=${url.database}
spring.datasource.username=${user.database}
spring.datasource.password=${password.database}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect

spring.flyway.enabled=true
spring.flyway.locations=classpath:/db
spring.flyway.schemas=${schema.database}
spring.flyway.url=${url.database}
spring.flyway.user=${user.database}
spring.flyway.password=${password.database}
```

Creacion de tabla usando flyway


A continuación procedemos a crear la migración en la carpeta db dentro de resources la cual llamaremos “V20240216215100__INIT_DB.sql” que contendrá la sentencia sql para la creación de la tabla “users” que utilizaremos para guardar los usuarios que crearemos mas adelante, dicha migración se ejecutara al deployar la aplicación si dicha migración no ha sido ejecutada anteriormente.

```
create table users(
    id bigint not null auto_increment, 
    name varchar(50) not null, 
    lastname varchar(50) not null, 
    age smallint not null, 
    primary key(id) 
);
```

## Creación de clase de configuración para Redis



Una vez especificadas las propiedades anteriores y la migración procedemos a crear la clase de configuración de redis

```
@EnableCaching
@Configuration
public class RedisConfig {

    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private Integer port; 

    @Bean
    public JedisConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<Long, User> userRedisTemplate(JedisConnectionFactory connectionFactory) {
        RedisTemplate<Long, User> userTemplate = new RedisTemplate<>();
        userTemplate.setConnectionFactory(connectionFactory);
        return userTemplate;
    }
}
```

La clase la llamamos RedisConfig y la anotamos con @Configuration para indicar a spring que esta clase debe ser inicializada dentro del contenedor de beans en un ámbito o contexto de configuración, en las variables host y port al llevar la anotación @Value le especificamos a spring que les de el valor de las propiedades que definimos anteriormente en el archivo application.properties.


El método connectionFactory es el que se encargara de entablar la conexión a redis y el userRedisTemplate definiremos nuestro template con los tipos de dato con el que estaremos trabajando. Recordemos que redis nos permite guardar datos a través de clave-valor por lo tanto el objeto RedisTemplate necesita de 2 tipos de datos para realizar su cometido, “Long” hará referencia al tipo de dato para la clave mientras que “User” sera el tipo de dato del valor asociado a dicha clave.


A continuación se elaborara la clase serivicio.



## Creacion de clase de servicio

```
@Service
public class UserService {

    private final UserRepository userRepository; 
    private final RedisTemplate<Long, User> userRedisTemplate; 

    public UserService(UserRepository userRepository, RedisTemplate<Long, User> userRedisTemplate) { 
        this.userRepository = userRepository; 
        this.userRedisTemplate = userRedisTemplate; 
    } 

    public User create(User user) { 
        return this.userRepository.save(user); 
    } 

    public User getUserByid(Long id) { 
        User user = this.userRedisTemplate.opsForValue().get(id); 

        if(Objects.nonNull(user)) 
            return user; 
 
        user = this.userRepository.findById(id) 
                .orElseThrow(() -> new RuntimeException("User not found by id")); 
        
        this.userRedisTemplate.opsForValue() 
                .set(id, user); 

        return user; 
    } 

    @Cacheable(value = "user", key = "#id") 
    public User getUserByid2(Long id) { 
        User user = this.userRepository.findById(id) 
                .orElseThrow(() -> new RuntimeException("User not found by id")); 
        return user; 

    }
```

La clase UserService es bastante sencilla, se le inyectan el userRedisTemplate, bean que definimos en la clase RedisConfig con el cual interactuaremos con redis y se inyecta userRepository que básicamente es un crud repository para la interacción con la base de datos mysql.


Esta clase nos ofrece 3 métodos, create que nos permite crear usuarios, y 2 métodos getUserById, el primero de ellos donde nosotros validamos que en redis este almacenado el usuario con el id que estamos buscando para retornarlo y de no ser así se busca en mysql para posteriormente almacenarlo en redis y retornarlo, el getUserbyId2 es muy similar solo que con la anotación @Cacheable especificamos el valor y cual es la key para dicho valor en este caso nuestra variable id lo cual almacena y busca automáticamente en redis sin tener que explícitamente indicarlo nosotros en código con en el primer ejemplo.



## Creacion de clase repository y controller

La clase UserRepository se define de la siguiente manera:

```
@Repository
public interface UserRepository extends CrudRepository<User, Long> {
}
```

Una vez teniendo definidos nuestras clases servicio y repositorio procederemos a desarrollar el controlador.

```
@RestController
@RequestMapping("user")

public class UserResource {

    private final UserService userService; 

    public UserResource(UserService userService) { 
        this.userService = userService; 
    } 

    @PostMapping 
    public User create(@RequestBody User user) { 
        return userService.create(user); 
    } 

    @GetMapping("/{id}") 
    public User getUserByid(@PathVariable Long id) {
        return this.userService.getUserByid(id); 
    } 

    @GetMapping("/short-way/{id}") 
    public User getUserByid2(@PathVariable Long id) { 
        return this.userService.getUserByid2(id); 
    } 
}
```

La clase UserResource expone 3 endpoints

**POST /user** que almacena en base de datos un usuarios

**GET /user/{id}** y **GET /user/short-way/{id}** que nos permiten extraer un usuario a través de su id utilizando los 2 métodos que vimos anteriormente en la clase UserService.



## Uso de Docker para ejecutar nuestra demo


Una ves teniendo la aplicación finalizada procedemos a crear un Dockerfile y un docker-compose.yml en la carpeta raíz de nuestro proyecto para la gestión y despliegue de los contenedores de nuestra aplicacion.

Dockerfile
```
FROM amazoncorretto:17-alpine-jdk
VOLUME /tmp
COPY build/libs/redis-spring-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

Creado el archivo Dockerfile procederemos a ejecutar la siguiente sentencia en la terminal posicionandonos en la carpeta raíz del proyecto “./gradlew build -x test && docker build -t spring-redis .” para compilar nuestro proyecto y crear la imagen de nuestra aplicación.


docker-compose.yml

```
version: '3.3'

services:
    mysql:
        image: mysql 
        restart: always 
        volumes: 
            - /Users/makotol/Documents/volumenes/mysql8-data:/var/lib/mysql 
        environment: 
            MYSQL_ROOT_PASSWORD: '1234567890' 
        ports:    
            - '33060:3306'
    redis:
        image: redis 
    spring-redis:
        image: spring-redis 
        environment: 
            - DEBUG=true
        ports:  
            - 8080:8080
            - 5105:5005
        depends_on: 
            - redis 
            - mysql 
        restart: on-failure:3 

networks:
    default:
        external: 
            name: red 
```

En una terminal procedemos a ejecutar el siguiente comando para crear la red necesaria para la comunicación entre los diferentes servicios “docker network create red”.


Una vez teniendo todos nuestros archivos preparados y creada la imagen de nuestra aplicación spring y la red en docker procederemos a abrir una terminal y posicionarnos en la carpeta raíz del proyecto, ejecutando la siguiente sentencia “docker-compose up -d” para inicializar todos los contenedores definidos en el docker-compose.yml y ya podremos interactuar con la aplicación.


Se realizaron algunas request para la creación de algunos usuarios.







En terminal ejecutamos el siguiente comando “docker excec –it redis-spring-redis-1 bash” de esta forma logramos acceder al bash del contenedor donde esta alojado redis e introducimos la siguiente sentencia “redis-cli” para acceder al cliente de redis, una vez dentro del cliente ejecutamos la sentencia “keys *” para ver la cantidad de claves de nuestros objetos guardados en redis, de momento no tenemos ningún objeto almacenado en redis.







Procedemos a hacer el llamado del endpoint getById.







Revisamos redis una vez mas y podremos observar como este elemento que hemos buscado se encuentra ya almacenado en nuestra popular base de datos de cache.

Primero utilizamos el comando “keys *” para ver cual clave guardo asociada a nuestro elemento, y con esa misma clave realizamos la extracción de el con el comando “get [key]”.







En este ejemplo vimos como conectar nuestra aplicación spring con mysql, redis, se utilizo flyway para crear la tabla users para el ejemplo, vimos también como utilizar redis para almacenar y extraer información de la popular base de datos en cache, se utilizo docker para crear los contenedores de nuestra aplicación  y docker-compose para llevar una gestión mas sencilla de nuestros contenedores para el deploy de la aplicación.
