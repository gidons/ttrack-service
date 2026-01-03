terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.45.0"
    }
  }
}

provider "azurerm" {
  features {}

  subscription_id = "428cde8a-576e-43cd-9f71-3259544547d3"
}

resource "azurerm_resource_group" "rg" {
  name     = "TTrack"
  location = "westus"
}

resource "azurerm_storage_account" "storage" {
  name                     = "shavitttrackwestus"
  resource_group_name      = azurerm_resource_group.rg.name
  location                 = azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_storage_container" "media_cont" {
  name                  = "song-media"
  storage_account_name    = azurerm_storage_account.storage.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "temp_cont" {
  name                  = "temp-files"
  storage_account_name    = azurerm_storage_account.storage.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "timed_data_cont" {
  name                  = "song-timed-data"
  storage_account_name    = azurerm_storage_account.storage.name
  container_access_type = "private"
}

resource "azurerm_storage_table" "songs_table" {
  name               = "Songs"
  storage_account_name = azurerm_storage_account.storage.name
}

resource "azurerm_storage_table" "async_tasks_table" {
  name               = "AsyncTasks"
  storage_account_name = azurerm_storage_account.storage.name
}
