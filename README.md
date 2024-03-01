## Objetivo

En esta guía veremos como conectar la base de datos en cache “*Redis*” con el popular framework de desarrollo para el lenguaje Java “*Spring*”, elaboraremos paso a paso un pequeño proyecto de ejemplo utilizando diversas tecnologías como Docker, Docker Compose, Redis, Java, Spring y MySQL.



En este pequeño proyecto realizaremos una aplicación Java-Spring que nos permitirá registrar usuarios en una base de datos MySQL y al consultarlos almacenar los usuarios en Redis para agilizar las próximas consultas de aquellos usuarios ya consultados, crearemos la tabla a través de migraciones con Flyway y deployaremos nuestra aplicación junto a MySQL y Redis en contenedores utilizando Docker y Docker Compose.



## Tecnologías a usar



Antes de empezar con nuestra guía se mostrarán una definición de cada tecnología que utilizaremos en este pequeño proyecto.



**Redis**: Es un almacén de estructura de datos de valores de clave en memoria rápido y de código abierto.



**MySQL**: Es el sistema de gestión de bases de datos relacional más extendido en la actualidad al estar basada en código abierto.



**Docker**: Es la tecnología de organización en contenedores que posibilita la creación y el uso de los contenedores de linux.



**Docker Compose**: Es una herramienta para definir y ejecutar aplicaciones de Docker de varios contenedores utilizando archivos YML.



**Java**: Es un lenguaje de programación ampliamente utilizado para codificar aplicaciones



**Spring Framework**: Es un framework de código abierto que da soporte para el desarrollo de aplicaciones y páginas webs basadas en Java. Se trata de uno de los entornos más populares y ayuda a los desarrolladores back-end a crear aplicaciones con un alto rendimiento empleando objetos de java sencillos.





## ¿Porqué usar Redis?



Como se mencionó anteriormente, Redis es una base de datos que se encuentra en memoria lo cual permite un rápido acceso a la información ahí almacenada, es simple y fácil de usar, ya que nos permite guardar y extraer información de forma sencilla y flexible por la gran cantidad de tipos de datos que esta pone a nuestra disposición además de ser de código abierto con una enorme comunidad muy activa a su alrededor.



Las formas de usar Redis son variadas, estas van desde guardados de sesiones y configuraciones en aplicaciones web, desde salas de chats incluso streaming. Sin embargo, el uso más frecuente que esta base de datos suele tener es para el almacenamiento y extracción de información muy constantemente solicitada, ya que disminuye la latencia de las peticiones aliviando la carga de servidores y gestores de base de datos.

Destacamos las principales ventajas de Redis:

- Menores tiempos de respuesta
- Estructura de datos flexible
- Simplicidad de uso


A pesar de existir algunas alternativas a Redis como por ejemplo Memcached, esta tiene algunas desventajas ante Redis. Memcached nos permite almacenar solo string mientras que Redis tiene una amplia variedad de estructuras de datos que nos ofrece para su utilización, Memcached solo permite almacenar hasta 1 mb por cada dato, Redis hasta 512 mb.



## Setup inicial

Crearemos nuestro proyecto spring el cual llamaremos spring-redis, utilizaremos la versión java 17 que es la que se encuentra instalada en mi equipo, gradle como gestor de dependencias y el empaquetado será un jar.



Una vez creado nuestro proyecto procederemos a incorporar las siguientes dependencias en el archivo build.gradle:



Las dos siguientes dependencias nos ayudarán a interactuar con Redis en nuestra aplicación.
```
implementation("redis.clients:jedis:5.1.0")
implementation("org.springframework.data:spring-data-redis:3.2.2")
```

Esta dependencia nos permitirá realizar la migración de base de datos a través de la librería Flyway.
```
implementation("org.flywaydb:flyway-core:10.8.1")
implementation("org.flywaydb:flyway-mysql:10.8.1")
```

Dentro de nuestro build.gradle incorporamos el plugin de Flyway.
```
id("org.flywaydb.flyway") version "10.8.1"
```


Agregamos también el task al final del mismo archivo.
```
flyway {
    url = "jdbc:mysql://mysql/pruebas" 
    user = "root" 
    password = "1234567890" 
    locations = ["classpath:db/"] 
}
```

Una vez incorporadas procederemos a buildear nuestro gradle para que las dependencias sean descargadas e incorporadas a nuestro proyecto para su utilización.



## Configuracion de properties

Incorporamos las propiedades necesarias para las conexiones de Redis, MySQL y la configuración de Flyway en el archivo application.properties
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

## Creación de tabla usando Flyway


A continuación procedemos a crear la migración en la carpeta db dentro de resources la cual llamaremos “V20240216215100__INIT_DB.sql” que contendrá la sentencia sql para la creación de la tabla “users” que utilizaremos para guardar los usuarios que crearemos más adelante, dicha migración se ejecutara al deployar la aplicación si dicha migración no ha sido ejecutada anteriormente.

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


Una vez especificadas las propiedades anteriores y la migración, procedemos a crear la clase de configuración de Redis.

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


El método connectionFactory es el que se encargara de entablar la conexión a Redis y el userRedisTemplate definiremos nuestro template con los tipos de dato con el que estaremos trabajando. Recordemos que Redis nos permite guardar datos a través de clave-valor, por lo tanto, el objeto RedisTemplate necesita de 2 tipos de datos para realizar su cometido, “Long” hará referencia al tipo de dato para la clave, mientras que “User” será el tipo de dato del valor asociado a dicha clave.


A continuación se elaborara la clase serivicio.



## Creación de clase de servicio

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

La clase UserService es bastante sencilla, se le inyectan el userRedisTemplate, bean que definimos en la clase RedisConfig con el cual interactuaremos con Redis y se inyecta userRepository que básicamente es un crud repository para la interacción con la base de datos MySQL.


Esta clase nos ofrece 3 métodos, create que nos permite crear usuarios, y 2 métodos getUserById, el primero de ellos donde nosotros validamos que en Redis esté almacenado el usuario con el id que estamos buscando para retornarlo y de no ser así se busca en mysql para posteriormente almacenarlo en Redis y retornarlo, el getUserbyId2 es muy similar solo que con la anotación @Cacheable especificamos el valor y cuál es la key para dicho valor en este caso nuestra variable id lo cual almacena y busca automáticamente en Redis sin tener que explícitamente indicarlo nosotros en código con en el primer ejemplo.

## Creación de clase repository y controller

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

**POST /user** que almacena en base de datos un usuario

**GET /user/{id}** y **GET /user/short-way/{id}** que nos permiten extraer un usuario a través de su id utilizando los 2 métodos que vimos anteriormente en la clase UserService.



## Uso de Docker para ejecutar nuestra demo


Una vez teniendo la aplicación finalizada procedemos a crear un Dockerfile y un docker-compose.yml en la carpeta raíz de nuestro proyecto para la gestión y despliegue de los contenedores de nuestra aplicación.
Dockerfile

```
FROM amazoncorretto:17-alpine-jdk
VOLUME /tmp
COPY build/libs/redis-spring-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

Creado el archivo Dockerfile procederemos a ejecutar la siguiente sentencia en la terminal para compilar nuestro proyecto y crear la imagen de nuestra aplicación.

```
./gradlew build -x test && docker build -t spring-redis .
```

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
        ports:
            - 6379:6379
        expose:
            - 6379
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

En una terminal procedemos a ejecutar el siguiente comando que creara la red necesaria para la comunicación entre los diferentes servicios:

```
docker network create red
```

Una vez teniendo todos nuestros archivos preparados, creada la imagen de nuestra aplicación Spring y la red en Docker procederemos a abrir una terminal y posicionarnos en la carpeta raíz del proyecto, ejecutando la siguiente sentencia para inicializar todos los contenedores definidos en el docker-compose.yml:

```
docker-compose up -d
```

Una vez todos nuestros contenedores disponibles podemos utilizar la aplicación.

Para la creación de algunos usuarios a través de la herramienta “Postman” lanzaremos peticiones al siguiente endpoint:

POST http://localhost:8080/user


![captura de postman donde se realiza la cracion de un usuario](images/Screenshot%201.png)

En la terminal ejecutamos el siguiente comando:

```
docker excec –it redis-spring-redis-1 bash
```

De esta forma logramos acceder al bash del contenedor donde está alojado Redis e introducimos la siguiente sentencia para introducirnos al cliente de Redis:

```
redis-cli
```

Una vez dentro del cliente ejecutamos la sentencia:

```
keys *
```

Con esto veremos la cantidad de claves de nuestros objetos guardados en Redis, de momento no tenemos ningún objeto almacenado.

![captura donde se consulta el listado vacio de keys en redis](images/Screenshot%202.png)

Procedemos a hacer el llamado del endpoint getById.

![captura de postman donde se utiliza el endpoint de consulta](images/Screenshot%203.png)

Revisamos Redis una vez más y podremos observar como este elemento que hemos buscado se encuentra ya almacenado en nuestra popular base de datos de cache.

Primero utilizamos el comando “keys *” para ver cuál clave guardo asociada a nuestro elemento, y con esa misma clave realizamos su extracción con el comando “get [key]”.

![captura donde se consulta el listado de keys y el elemento almacenado](images/Screenshot%204.png)

En este ejemplo vimos como conectar nuestra aplicación Spring con MySQL, Redis, se utilizó Flayway para crear la tabla users para el ejemplo, vimos también como utilizar Redis para almacenar y extraer información de la popular base de datos en cache, se utilizó Docker para crear los contenedores de nuestra aplicación  y Docker Compose para llevar una gestión más sencilla de nuestros contenedores para el deploy de la aplicación.


## Documentación

https://redis.io/docs/about/

https://dev.mysql.com/doc/refman/8.3/en/introduction.html

https://dev.java/learn/getting-started/

https://spring.io/learn

https://docs.docker.com/

https://documentation.red-gate.com/flyway/quickstart-how-flyway-works

# Referencias

https://aws.amazon.com/es/elasticache/what-is-redis

https://openwebinars.net/blog/que-es-mysql

https://www.redhat.com/es/topics/containers/what-is-docker

https://learn.microsoft.com/es-es/azure/ai-services/containers/docker-compose-recipe

https://aws.amazon.com/es/what-is/java

https://www.ibm.com/mx-es/topics/java-spring-boot 

 
