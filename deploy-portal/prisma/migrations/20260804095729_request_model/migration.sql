/*
  Warnings:

  - You are about to drop the `Deployment` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `DeploymentEvent` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Project` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Release` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ReleaseItem` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the column `projectId` on the `Environment` table. All the data in the column will be lost.

*/
-- DropIndex
DROP INDEX "Deployment_releaseId_environmentId_key";

-- DropIndex
DROP INDEX "Project_name_key";

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "Deployment";
PRAGMA foreign_keys=on;

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "DeploymentEvent";
PRAGMA foreign_keys=on;

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "Project";
PRAGMA foreign_keys=on;

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "Release";
PRAGMA foreign_keys=on;

-- DropTable
PRAGMA foreign_keys=off;
DROP TABLE "ReleaseItem";
PRAGMA foreign_keys=on;

-- CreateTable
CREATE TABLE "Application" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "name" TEXT NOT NULL,
    "mhrsOnly" BOOLEAN NOT NULL DEFAULT false
);

-- CreateTable
CREATE TABLE "DeploymentRequest" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "environmentId" INTEGER NOT NULL,
    "applicationId" INTEGER NOT NULL,
    "releaseDate" DATETIME NOT NULL,
    "branch" TEXT NOT NULL,
    "hasScript" BOOLEAN NOT NULL DEFAULT false,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "rejectReason" TEXT,
    "requester" TEXT NOT NULL DEFAULT 'volkan',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "DeploymentRequest_environmentId_fkey" FOREIGN KEY ("environmentId") REFERENCES "Environment" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "DeploymentRequest_applicationId_fkey" FOREIGN KEY ("applicationId") REFERENCES "Application" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ScriptFile" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "requestId" INTEGER NOT NULL,
    "dbType" TEXT NOT NULL,
    "fileName" TEXT NOT NULL,
    "storedName" TEXT NOT NULL,
    "uploadedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ScriptFile_requestId_fkey" FOREIGN KEY ("requestId") REFERENCES "DeploymentRequest" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Environment" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "name" TEXT NOT NULL
);
INSERT INTO "new_Environment" ("id", "name") SELECT "id", "name" FROM "Environment";
DROP TABLE "Environment";
ALTER TABLE "new_Environment" RENAME TO "Environment";
CREATE UNIQUE INDEX "Environment_name_key" ON "Environment"("name");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

-- CreateIndex
CREATE UNIQUE INDEX "Application_name_key" ON "Application"("name");
