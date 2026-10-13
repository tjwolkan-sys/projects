/*
  Warnings:

  - A unique constraint covering the columns `[approvalToken]` on the table `DeploymentRequest` will be added. If there are existing duplicate values, this will fail.

*/
-- AlterTable
ALTER TABLE "DeploymentRequest" ADD COLUMN "approvalToken" TEXT;
ALTER TABLE "DeploymentRequest" ADD COLUMN "approvedAt" DATETIME;
ALTER TABLE "DeploymentRequest" ADD COLUMN "approvedBy" TEXT;

-- CreateIndex
CREATE UNIQUE INDEX "DeploymentRequest_approvalToken_key" ON "DeploymentRequest"("approvalToken");
