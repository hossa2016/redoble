# Prisma Widgets: reglas de R8.
# Los workers de WorkManager se crean por nombre de clase.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# Receptores, servicios y el proveedor del widget se declaran en el Manifest (R8 ya los conserva).
