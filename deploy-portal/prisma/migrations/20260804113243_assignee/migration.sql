-- CreateTable
CREATE TABLE "Assignee" (
    "id" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "name" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "active" BOOLEAN NOT NULL DEFAULT true
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_DeploymentRequest" (
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
    "approvalToken" TEXT,
    "approvedBy" TEXT,
    "approvedAt" DATETIME,
    "assigneeId" INTEGER,
    "assignmentNote" TEXT,
    "assignedAt" DATETIME,
    CONSTRAINT "DeploymentRequest_environmentId_fkey" FOREIGN KEY ("environmentId") REFERENCES "Environment" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "DeploymentRequest_applicationId_fkey" FOREIGN KEY ("applicationId") REFERENCES "Application" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "DeploymentRequest_assigneeId_fkey" FOREIGN KEY ("assigneeId") REFERENCES "Assignee" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_DeploymentRequest" ("applicationId", "approvalToken", "approvedAt", "approvedBy", "branch", "createdAt", "environmentId", "hasScript", "id", "rejectReason", "releaseDate", "requester", "status", "updatedAt") SELECT "applicationId", "approvalToken", "approvedAt", "approvedBy", "branch", "createdAt", "environmentId", "hasScript", "id", "rejectReason", "releaseDate", "requester", "status", "updatedAt" FROM "DeploymentRequest";
DROP TABLE "DeploymentRequest";
ALTER TABLE "new_DeploymentRequest" RENAME TO "DeploymentRequest";
CREATE UNIQUE INDEX "DeploymentRequest_approvalToken_key" ON "DeploymentRequest"("approvalToken");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

-- CreateIndex
CREATE UNIQUE INDEX "Assignee_email_key" ON "Assignee"("email");
