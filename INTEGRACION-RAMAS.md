# Integración de inventario, dev y tracking

Se resolvió el merge de `origin/dev` (`c96f26a`) en
`feature/SCRUM-70-endpoints-inventario-y-catalogo` y después se incorporó
`origin/feature/SCRUM-72-order-tracking` (`76d6c94`). Git reconoce los commits
compartidos: no vuelve a aplicar los cambios de dev incluidos en tracking.

## Funciones conservadas

- `/api/admin/products`: inventario, filtros, estadísticas, exportaciones y gestión de imágenes.
- `/api/admin/categories`: búsqueda, jerarquía, edición y desactivación de categorías.
- `/api/products` y `/api/categories`: catálogo público y escrituras protegidas para ADMIN.
- Autenticación de dev, Google, recuperación de contraseña, perfiles y CORS configurable.
- `/api/tracking/{orderId}`: consulta del seguimiento; `POST /api/tracking`: registro para ADMIN.

Se añadió `OrderRepository`, que TrackingService importaba pero no existía en
la rama de origen. El bloque final de `schema.sql` e `init.sql` crea de forma
idempotente `store_schema.orders` y `store_schema.tracking_events`, sin sustituir
las tablas de paquetería de `package_schema`.

## Validación y puesta en marcha

El pom no necesitó cambios: las tres ramas comparten las dependencias necesarias,
incluidas POI y OpenPDF para exportaciones. Se compiló con el JDK 25 instalado,
manteniendo `release 21`. Pasaron 14 pruebas de servicios, incluidas regresiones
para tracking, generación de slug y eliminación de categorías con productos.

Comando usado en PowerShell (el agente evita que Mockito intente adjuntarse al JDK):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
.\mvnw.cmd -o '-Dtest=*ServiceTest' '-DargLine=-javaagent:C:/Users/Jair/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' test
```

No se ejecutó `ApplicationTests.contextLoads`, que requiere configuración externa
y conexión a PostgreSQL. No se modificó ninguna base de datos. Antes de usar
tracking, aplicar el bloque final de SQL de seguimiento de pedidos a la base
correspondiente: `ddl-auto=none` no crea tablas automáticamente. Configurar las
variables de entorno documentadas en `.env.example`.

Los cambios están únicamente en el repositorio local. Para publicarlos, subir
la rama propia y abrir un pull request con base `dev`:

```powershell
git push origin feature/SCRUM-70-endpoints-inventario-y-catalogo
```

Si dev avanza antes de integrar el pull request, hacer `git fetch origin`,
integrar `origin/dev` de nuevo y repetir las verificaciones. Resolver cada
conflicto por comportamiento; aceptar un archivo completo de un solo lado
puede eliminar funcionalidades del otro.
