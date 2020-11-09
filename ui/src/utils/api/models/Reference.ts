/* tslint:disable */
/* eslint-disable */
/**
 * Nessie API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 0.2.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import {
    Branch,
    Hash,
    Tag,
    BranchFromJSONTyped,
    BranchToJSON,
    HashFromJSONTyped,
    HashToJSON,
    TagFromJSONTyped,
    TagToJSON,
} from './';

/**
 * @type Reference
 * @export
 */
export type Reference = { type: 'TAG' } & Tag | { type: 'BRANCH' } & Branch | { type: 'HASH' } & Hash;

export function ReferenceFromJSON(json: any): Reference {
    return ReferenceFromJSONTyped(json, false);
}

export function ReferenceFromJSONTyped(json: any, ignoreDiscriminator: boolean): Reference {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    switch (json['type']) {
        case 'TAG':
            return {...TagFromJSONTyped(json, true), type: 'TAG'};
        case 'BRANCH':
            return {...BranchFromJSONTyped(json, true), type: 'BRANCH'};
        case 'HASH':
            return {...HashFromJSONTyped(json, true), type: 'HASH'};
        default:
            throw new Error(`No variant of Reference exists with 'type=${json['type']}'`);
    }
}

export function ReferenceToJSON(value?: Reference | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    switch (value['type']) {
        case 'TAG':
            return TagToJSON(value);
        case 'BRANCH':
            return BranchToJSON(value);
        case 'HASH':
            return HashToJSON(value);
        default:
            throw new Error(`No variant of Reference exists with 'type=${value['type']}'`);
    }
}

