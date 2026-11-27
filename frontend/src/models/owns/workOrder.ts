import { Audit } from './audit';
import Request from './request';
import Team from './team';
import Asset from './asset';
import File from './file';
import Location from './location';
import Category from './category';
import { OwnUser, UserMiniDTO } from '../user';
import { CustomerMiniDTO } from './customer';
import PreventiveMaintenance from './preventiveMaintenance';
import { WorkOrderBase } from './workOrderBase';
import { CustomField } from './customField';
import { SlaStatus } from './slaPolicy';
import { CostCenterMini } from './costCenter';

export type Priority = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

export default interface WorkOrder extends WorkOrderBase {
  category: Category | null;
  id: number;
  completedBy: OwnUser;
  completedOn: string;
  archived: boolean;
  parentRequest: Request;
  parentPreventiveMaintenance: PreventiveMaintenance;
  signature: string;
  feedback: string;
  requiredSignature: boolean;
  status: string;
  audioDescription: File;
  customId: string;
  serviceContract?: { id: number; name: string; contractNumber: string } | null;
  slaPolicy?: { id: number; name: string } | null;
  slaFirstResponseDueAt?: string | null;
  slaResolutionDueAt?: string | null;
  firstRespondedAt?: string | null;
  slaStatus?: SlaStatus | null;
  costCenter?: CostCenterMini | null;
  //parentPreventiveMaintenance:
}

export interface WorkOrderMini {
  id: number;
  title: string;
  status: string;
  createdAt: string;
}
