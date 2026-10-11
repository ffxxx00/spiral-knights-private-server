# Evaluación inicial de Clyde/Tudey

Referencia fijada: `8b0c64bb4f37b0d9af6e6b154867ffd095f4cc01`.

Se verificaron los hashes de ocho archivos originales. Sus fuentes y licencias
permanecen locales en analysis/upstream/clyde/. No se añadieron al runtime.

La comparación usa campos públicos de instancia de cuatro familias de configuración
y los campos/descriptores del bytecode congelado. El scanner de Java es limitado:
esto identifica diferencias de esquema, no demuestra compatibilidad binaria,
de comportamiento ni una compilación completa del servidor original.

| Familia | Clases comunes | Solo upstream | Solo cliente | Diferencias de campos |
| --- | ---: | ---: | ---: | ---: |
| Action | 37 | 0 | 2 | 0 |
| Condition | 20 | 0 | 0 | 0 |
| Expression | 31 | 0 | 0 | 0 |
| Handler | 28 | 0 | 0 | 0 |

## Uso previsto

ActionLogic, ConditionLogic, ExpressionLogic y HandlerLogic aportan referencias
de semántica para acciones comunes. Primero contrastar las clases concretas y
sus dependencias; después adaptar un contrato pequeño y comprobarlo con casos
controlados. No sustituir el JAR congelado por una biblioteca moderna.

Las extensiones ProjectX de daño, estados, IA y progresión siguen requiriendo
reconstrucción a partir del cliente, catálogos y resultados observados.

Consultar el detalle local compatibility.json antes de escoger una familia.
El manifiesto tools/reverse/clyde-reference.json conserva URLs y SHA-256.
