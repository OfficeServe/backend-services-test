package com.officeserve.documentservice.models

case class Address(addressLine1: String,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   postCode: String,
                   city: String,
                   additionalInfo: Option[String] = None)